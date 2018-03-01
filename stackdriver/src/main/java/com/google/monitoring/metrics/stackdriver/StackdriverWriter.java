// Copyright 2017 Google LLC. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.monitoring.metrics.stackdriver;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.BucketOptions;
import com.google.api.services.monitoring.v3.model.CreateTimeSeriesRequest;
import com.google.api.services.monitoring.v3.model.Distribution;
import com.google.api.services.monitoring.v3.model.Explicit;
import com.google.api.services.monitoring.v3.model.Exponential;
import com.google.api.services.monitoring.v3.model.LabelDescriptor;
import com.google.api.services.monitoring.v3.model.Linear;
import com.google.api.services.monitoring.v3.model.Metric;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeInterval;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.api.services.monitoring.v3.model.TypedValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.RateLimiter;
import com.google.monitoring.metrics.CustomFitter;
import com.google.monitoring.metrics.DistributionFitter;
import com.google.monitoring.metrics.ExponentialFitter;
import com.google.monitoring.metrics.IncrementableMetric;
import com.google.monitoring.metrics.LinearFitter;
import com.google.monitoring.metrics.MetricPoint;
import com.google.monitoring.metrics.MetricRegistryImpl;
import com.google.monitoring.metrics.MetricSchema.Kind;
import com.google.monitoring.metrics.MetricWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Metrics writer for Google Cloud Monitoring V3
 *
 * <p>This class communicates with the API via HTTP. In order to increase throughput and minimize
 * CPU, it buffers points to be written until it has {@code maxPointsPerRequest} points buffered or
 * until {@link #flush()} is called.
 *
 * @see <a href="https://cloud.google.com/monitoring/api/v3/">Introduction to the Stackdriver
 *     Monitoring API</a>
 */
// TODO(shikhman): add retry logic
@NotThreadSafe
public class StackdriverWriter implements MetricWriter {

  /**
   * A counter representing the total number of points pushed. Has {@link Kind} and metric value
   * types as labels.
   */
  private static final IncrementableMetric pushedPoints =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/metrics/stackdriver/points_pushed",
              "Count of points pushed to Stackdriver Monitoring API.",
              "Points Pushed",
              ImmutableSet.of(
                  com.google.monitoring.metrics.LabelDescriptor.create("kind", "Metric Kind"),
                  com.google.monitoring.metrics.LabelDescriptor.create(
                      "valueType", "Metric Value Type")));

  private static final String METRIC_DOMAIN = "custom.googleapis.com";
  private static final String LABEL_VALUE_TYPE = "STRING";
  private static final Logger logger = Logger.getLogger(StackdriverWriter.class.getName());
  // A map of native type to the equivalent Stackdriver metric type.
  private static final ImmutableMap<Class<?>, String> ENCODED_METRIC_TYPES =
      new ImmutableMap.Builder<Class<?>, String>()
          .put(Long.class, "INT64")
          .put(Double.class, "DOUBLE")
          .put(Boolean.class, "BOOL")
          .put(String.class, "STRING")
          .put(com.google.monitoring.metrics.Distribution.class, "DISTRIBUTION")
          .build();
  // A map of native kind to the equivalent Stackdriver metric kind.
  private static final ImmutableMap<String, String> ENCODED_METRIC_KINDS =
      new ImmutableMap.Builder<String, String>()
          .put(Kind.GAUGE.name(), "GAUGE")
          .put(Kind.CUMULATIVE.name(), "CUMULATIVE")
          .build();
  private static final String FLUSH_OVERFLOW_ERROR = "Cannot flush more than 200 points at a time";
  private static final String METRIC_KIND_ERROR =
      "Unrecognized metric kind, must be one of "
          + Joiner.on(",").join(ENCODED_METRIC_KINDS.keySet());
  private static final String METRIC_TYPE_ERROR =
      "Unrecognized metric type, must be one of "
          + Joiner.on(" ").join(ENCODED_METRIC_TYPES.keySet());
  private static final String METRIC_LABEL_COUNT_ERROR =
      "Metric label value count does not match its MetricDescriptor's label count";

  private final MonitoredResource monitoredResource;
  private final Queue<TimeSeries> timeSeriesBuffer;
  /**
   * A local cache of MetricDescriptors. A metric's metadata (name, kind, type, label definitions)
   * must be registered before points for the metric can be pushed.
   */
  private final HashMap<com.google.monitoring.metrics.Metric<?>, MetricDescriptor>
      registeredDescriptors = new HashMap<>();

  private final String projectResource;
  private final Monitoring monitoringClient;
  private final int maxPointsPerRequest;
  private final RateLimiter rateLimiter;

  /**
   * Constructs a {@link StackdriverWriter}.
   *
   * <p>The monitoringClient must have read and write permissions to the Cloud Monitoring API v3 on
   * the provided project.
   */
  public StackdriverWriter(
      Monitoring monitoringClient,
      String project,
      MonitoredResource monitoredResource,
      int maxQps,
      int maxPointsPerRequest) {
    this.monitoringClient = checkNotNull(monitoringClient);
    this.projectResource = "projects/" + checkNotNull(project);
    this.monitoredResource = monitoredResource;
    this.maxPointsPerRequest = maxPointsPerRequest;
    this.timeSeriesBuffer = new ArrayDeque<>(maxPointsPerRequest);
    this.rateLimiter = RateLimiter.create(maxQps);
  }

  @VisibleForTesting
  static ImmutableList<LabelDescriptor> encodeLabelDescriptors(
      ImmutableSet<com.google.monitoring.metrics.LabelDescriptor> labelDescriptors) {
    List<LabelDescriptor> stackDriverLabelDescriptors = new ArrayList<>(labelDescriptors.size());

    for (com.google.monitoring.metrics.LabelDescriptor labelDescriptor : labelDescriptors) {
      stackDriverLabelDescriptors.add(
          new LabelDescriptor()
              .setKey(labelDescriptor.name())
              .setDescription(labelDescriptor.description())
              .setValueType(LABEL_VALUE_TYPE));
    }

    return ImmutableList.copyOf(stackDriverLabelDescriptors);
  }

  @VisibleForTesting
  static MetricDescriptor encodeMetricDescriptor(com.google.monitoring.metrics.Metric<?> metric) {
    return new MetricDescriptor()
        .setType(METRIC_DOMAIN + metric.getMetricSchema().name())
        .setDescription(metric.getMetricSchema().description())
        .setDisplayName(metric.getMetricSchema().valueDisplayName())
        .setValueType(ENCODED_METRIC_TYPES.get(metric.getValueClass()))
        .setLabels(encodeLabelDescriptors(metric.getMetricSchema().labels()))
        .setMetricKind(ENCODED_METRIC_KINDS.get(metric.getMetricSchema().kind().name()));
  }

  /** Encodes and writes a metric point to Stackdriver. The point may be buffered. */
  @Override
  public <V> void write(com.google.monitoring.metrics.MetricPoint<V> point) throws IOException {
    checkNotNull(point);

    TimeSeries timeSeries = getEncodedTimeSeries(point);
    timeSeriesBuffer.add(timeSeries);

    logger.fine(String.format("Enqueued metric %s for writing", timeSeries.getMetric().getType()));
    if (timeSeriesBuffer.size() == maxPointsPerRequest) {
      flush();
    }
  }

  /** Flushes all buffered metric points to Stackdriver. This call is blocking. */
  @Override
  public void flush() throws IOException {
    checkState(timeSeriesBuffer.size() <= 200, FLUSH_OVERFLOW_ERROR);

    // Return early; Stackdriver throws errors if we attempt to send empty requests.
    if (timeSeriesBuffer.isEmpty()) {
      logger.fine("Attempted to flush with no pending points, doing nothing");
      return;
    }

    ImmutableList<TimeSeries> timeSeriesList = ImmutableList.copyOf(timeSeriesBuffer);
    timeSeriesBuffer.clear();

    CreateTimeSeriesRequest request = new CreateTimeSeriesRequest().setTimeSeries(timeSeriesList);

    rateLimiter.acquire();
    monitoringClient.projects().timeSeries().create(projectResource, request).execute();

    for (TimeSeries timeSeries : timeSeriesList) {
      pushedPoints.increment(timeSeries.getMetricKind(), timeSeries.getValueType());
    }
    logger.fine(String.format("Flushed %d metrics to Stackdriver", timeSeriesList.size()));
  }

  /**
   * Registers a metric's {@link MetricDescriptor} with the Monitoring API.
   *
   * @param metric the metric to be registered.
   * @see <a
   *     href="https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.metricDescriptors">Stackdriver
   *     MetricDescriptor API</a>
   */
  @VisibleForTesting
  MetricDescriptor registerMetric(final com.google.monitoring.metrics.Metric<?> metric)
      throws IOException {
    if (registeredDescriptors.containsKey(metric)) {
      logger.fine(
          String.format("Using existing metric descriptor %s", metric.getMetricSchema().name()));
      return registeredDescriptors.get(metric);
    }

    MetricDescriptor descriptor = encodeMetricDescriptor(metric);

    rateLimiter.acquire();
    // We try to create a descriptor, but it may have been created already, so we re-fetch it on
    // failure
    try {
      descriptor =
          monitoringClient
              .projects()
              .metricDescriptors()
              .create(projectResource, descriptor)
              .execute();
      logger.info(String.format("Registered new metric descriptor %s", descriptor.getType()));
    } catch (GoogleJsonResponseException jsonException) {
      // Not the error we were expecting, just give up
      if (!"ALREADY_EXISTS".equals(jsonException.getStatusMessage())) {
        throw jsonException;
      }

      descriptor =
          monitoringClient
              .projects()
              .metricDescriptors()
              .get(projectResource + "/metricDescriptors/" + descriptor.getType())
              .execute();

      logger.info(
          String.format("Fetched existing metric descriptor %s", metric.getMetricSchema().name()));
    }

    registeredDescriptors.put(metric, descriptor);

    return descriptor;
  }

  private static TimeInterval encodeTimeInterval(Range<Instant> nativeInterval, Kind metricKind) {

    TimeInterval encodedInterval =
        new TimeInterval().setStartTime(nativeInterval.lowerEndpoint().toString());

    Instant endTimestamp =
        nativeInterval.isEmpty() && metricKind != Kind.GAUGE
            ? nativeInterval.upperEndpoint().plusMillis(1)
            : nativeInterval.upperEndpoint();

    return encodedInterval.setEndTime(endTimestamp.toString());
  }

  private static BucketOptions encodeBucketOptions(DistributionFitter fitter) {
    BucketOptions bucketOptions = new BucketOptions();

    if (fitter instanceof LinearFitter) {
      LinearFitter linearFitter = (LinearFitter) fitter;

      bucketOptions.setLinearBuckets(
          new Linear()
              .setNumFiniteBuckets(linearFitter.boundaries().size() - 1)
              .setWidth(linearFitter.width())
              .setOffset(linearFitter.offset()));
    } else if (fitter instanceof ExponentialFitter) {
      ExponentialFitter exponentialFitter = (ExponentialFitter) fitter;

      bucketOptions.setExponentialBuckets(
          new Exponential()
              .setNumFiniteBuckets(exponentialFitter.boundaries().size() - 1)
              .setGrowthFactor(exponentialFitter.base())
              .setScale(exponentialFitter.scale()));
    } else if (fitter instanceof CustomFitter) {
      bucketOptions.setExplicitBuckets(new Explicit().setBounds(fitter.boundaries().asList()));
    } else {
      throw new IllegalArgumentException("Illegal DistributionFitter type");
    }

    return bucketOptions;
  }

  private static List<Long> encodeDistributionPoints(
      com.google.monitoring.metrics.Distribution distribution) {
    return distribution.intervalCounts().asMapOfRanges().values().asList();
  }

  private static Distribution encodeDistribution(
      com.google.monitoring.metrics.Distribution nativeDistribution) {
    return new Distribution()
        .setMean(nativeDistribution.mean())
        .setCount(nativeDistribution.count())
        .setSumOfSquaredDeviation(nativeDistribution.sumOfSquaredDeviation())
        .setBucketOptions(encodeBucketOptions(nativeDistribution.distributionFitter()))
        .setBucketCounts(encodeDistributionPoints(nativeDistribution));
  }

  /**
   * Encodes a {@link MetricPoint} into a Stackdriver {@link TimeSeries}.
   *
   * <p>This method will register the underlying {@link com.google.monitoring.metrics.Metric} as a
   * Stackdriver {@link MetricDescriptor}.
   *
   * @see <a href="https://cloud.google.com/monitoring/api/ref_v3/rest/v3/TimeSeries">Stackdriver
   *     TimeSeries API</a>
   */
  @VisibleForTesting
  <V> TimeSeries getEncodedTimeSeries(com.google.monitoring.metrics.MetricPoint<V> point)
      throws IOException {
    com.google.monitoring.metrics.Metric<V> metric = point.metric();
    try {
      checkArgument(
          ENCODED_METRIC_KINDS.containsKey(metric.getMetricSchema().kind().name()),
          METRIC_KIND_ERROR);
      checkArgument(ENCODED_METRIC_TYPES.containsKey(metric.getValueClass()), METRIC_TYPE_ERROR);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage());
    }

    MetricDescriptor descriptor = registerMetric(metric);

    if (point.labelValues().size() != point.metric().getMetricSchema().labels().size()) {
      throw new IOException(METRIC_LABEL_COUNT_ERROR);
    }

    V value = point.value();
    TypedValue encodedValue = new TypedValue();
    Class<?> valueClass = metric.getValueClass();

    if (valueClass == Long.class) {
      encodedValue.setInt64Value((Long) value);
    } else if (valueClass == Double.class) {
      encodedValue.setDoubleValue((Double) value);
    } else if (valueClass == Boolean.class) {
      encodedValue.setBoolValue((Boolean) value);
    } else if (valueClass == String.class) {
      encodedValue.setStringValue((String) value);
    } else if (valueClass == com.google.monitoring.metrics.Distribution.class) {
      encodedValue.setDistributionValue(
          encodeDistribution((com.google.monitoring.metrics.Distribution) value));
    } else {
      // This is unreachable because the precondition checks will catch all NotSerializable
      // exceptions.
      throw new IllegalArgumentException("Invalid metric valueClass: " + metric.getValueClass());
    }

    Point encodedPoint =
        new Point()
            .setInterval(encodeTimeInterval(point.interval(), metric.getMetricSchema().kind()))
            .setValue(encodedValue);

    List<LabelDescriptor> encodedLabels = descriptor.getLabels();
    // The MetricDescriptors returned by the GCM API have null fields rather than empty lists
    encodedLabels = encodedLabels == null ? ImmutableList.of() : encodedLabels;

    ImmutableMap.Builder<String, String> labelValues = new ImmutableMap.Builder<>();
    int i = 0;
    for (LabelDescriptor labelDescriptor : encodedLabels) {
      labelValues.put(labelDescriptor.getKey(), point.labelValues().get(i++));
    }

    Metric encodedMetric =
        new Metric().setType(descriptor.getType()).setLabels(labelValues.build());

    return new TimeSeries()
        .setMetric(encodedMetric)
        .setPoints(ImmutableList.of(encodedPoint))
        .setResource(monitoredResource)
        // these two attributes are ignored by the API, we set them here to use elsewhere
        // for internal metrics.
        .setMetricKind(descriptor.getMetricKind())
        .setValueType(descriptor.getValueType());
  }
}
