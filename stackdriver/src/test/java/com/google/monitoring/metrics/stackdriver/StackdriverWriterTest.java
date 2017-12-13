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

import static com.google.common.truth.Truth.assertThat;
import static com.google.monitoring.metrics.JUnitBackports.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.BucketOptions;
import com.google.api.services.monitoring.v3.model.CreateTimeSeriesRequest;
import com.google.api.services.monitoring.v3.model.Explicit;
import com.google.api.services.monitoring.v3.model.Exponential;
import com.google.api.services.monitoring.v3.model.Linear;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.monitoring.metrics.CustomFitter;
import com.google.monitoring.metrics.Distribution;
import com.google.monitoring.metrics.ExponentialFitter;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.LinearFitter;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricPoint;
import com.google.monitoring.metrics.MetricSchema;
import com.google.monitoring.metrics.MetricSchema.Kind;
import com.google.monitoring.metrics.MutableDistribution;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

/** Unit tests for {@link StackdriverWriter}. */
@RunWith(MockitoJUnitRunner.class)
public class StackdriverWriterTest {

  @Mock private Monitoring client;
  @Mock private Monitoring.Projects projects;
  @Mock private Monitoring.Projects.MetricDescriptors metricDescriptors;
  @Mock private Monitoring.Projects.MetricDescriptors.Get metricDescriptorGet;
  @Mock private Monitoring.Projects.TimeSeries timeSeries;
  @Mock private Monitoring.Projects.MetricDescriptors.Create metricDescriptorCreate;
  @Mock private Monitoring.Projects.TimeSeries.Create timeSeriesCreate;
  @Mock private Metric<Long> metric;
  @Mock private Metric<Boolean> boolMetric;
  @Mock private Metric<Distribution> distributionMetric;
  private static final String PROJECT = "PROJECT";
  private static final int MAX_QPS = 10;
  private static final int MAX_POINTS_PER_REQUEST = 10;
  private static final MonitoredResource MONITORED_RESOURCE = new MonitoredResource();

  @Before
  public void setUp() throws Exception {
    when(metric.getValueClass()).thenReturn(Long.class);
    when(metric.getCardinality()).thenReturn(1);
    when(metric.getMetricSchema())
        .thenReturn(
            MetricSchema.create(
                "/name",
                "desc",
                "vdn",
                Kind.CUMULATIVE,
                ImmutableSet.of(LabelDescriptor.create("label1", "desc1"))));
    // Store in an intermediate value, because Mockito hates when mocks are evaluated inside of
    // thenReturn() methods.
    MetricPoint<Long> longPoint =
        MetricPoint.create(
            metric, ImmutableList.of("value1"), new Instant(1337), new Instant(1338), 5L);
    when(metric.getTimestampedValues()).thenReturn(ImmutableList.of(longPoint));

    when(boolMetric.getValueClass()).thenReturn(Boolean.class);
    when(boolMetric.getMetricSchema())
        .thenReturn(
            MetricSchema.create(
                "/name",
                "desc",
                "vdn",
                Kind.GAUGE,
                ImmutableSet.of(LabelDescriptor.create("label1", "desc1"))));
    // Store in an intermediate value, because Mockito hates when mocks are evaluated inside of
    // thenReturn() methods.
    MetricPoint<Boolean> boolPoint =
        MetricPoint.create(boolMetric, ImmutableList.of("foo"), new Instant(1337), true);
    when(boolMetric.getTimestampedValues()).thenReturn(ImmutableList.of(boolPoint));

    when(distributionMetric.getMetricSchema())
        .thenReturn(
            MetricSchema.create(
                "/name",
                "desc",
                "vdn",
                Kind.GAUGE,
                ImmutableSet.of(LabelDescriptor.create("label1", "desc1"))));
    when(distributionMetric.getValueClass()).thenReturn(Distribution.class);

    MetricDescriptor descriptor = StackdriverWriter.encodeMetricDescriptor(metric);
    when(client.projects()).thenReturn(projects);
    when(projects.metricDescriptors()).thenReturn(metricDescriptors);
    when(projects.timeSeries()).thenReturn(timeSeries);
    when(metricDescriptors.create(anyString(), any(MetricDescriptor.class)))
        .thenReturn(metricDescriptorCreate);
    when(metricDescriptorCreate.execute()).thenReturn(descriptor);
    when(metricDescriptors.get(anyString())).thenReturn(metricDescriptorGet);
    when(metricDescriptorGet.execute()).thenReturn(descriptor);
    when(timeSeries.create(anyString(), any(CreateTimeSeriesRequest.class)))
        .thenReturn(timeSeriesCreate);
  }

  @Test
  public void testWrite_maxPoints_flushes() throws Exception {
    StackdriverWriter writer =
        spy(
            new StackdriverWriter(
                client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST));


    for (int i = 0; i < MAX_POINTS_PER_REQUEST; i++) {
      for (MetricPoint<?> point : metric.getTimestampedValues()) {
        writer.write(point);
      }
    }

    verify(writer).flush();
  }

  @Test
  public void testWrite_lessThanMaxPoints_doesNotFlush() throws Exception {
    StackdriverWriter writer =
        spy(
            new StackdriverWriter(
                client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST));
    for (int i = 0; i < MAX_POINTS_PER_REQUEST - 1; i++) {
      for (MetricPoint<?> point : metric.getTimestampedValues()) {
        writer.write(point);
      }
    }

    verify(writer, never()).flush();
  }

  @Test
  public void testWrite_invalidMetricType_throwsException() throws Exception {
    when(metric.getValueClass()).thenAnswer((Answer<Class<?>>) invocation -> Object.class);
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    for (MetricPoint<?> point : metric.getTimestampedValues()) {
      assertThrows(IOException.class, () -> writer.write(point));
    }
  }

  @Test
  public void testWrite_ManyPoints_flushesTwice() throws Exception {
    StackdriverWriter writer =
        spy(
            new StackdriverWriter(
                client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST));

    for (int i = 0; i < MAX_POINTS_PER_REQUEST * 2; i++) {
      for (MetricPoint<?> point : metric.getTimestampedValues()) {
        writer.write(point);
      }
    }

    verify(writer, times(2)).flush();
  }

  @Test
  public void testRegisterMetric_registersWithStackdriver() throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    writer.registerMetric(metric);

    verify(
            client
                .projects()
                .metricDescriptors()
                .create(PROJECT, StackdriverWriter.encodeMetricDescriptor(metric)))
        .execute();
  }

  @Test
  public void registerMetric_doesNotReregisterDupe() throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    writer.registerMetric(metric);
    writer.registerMetric(metric);

    verify(
            client
                .projects()
                .metricDescriptors()
                .create(PROJECT, StackdriverWriter.encodeMetricDescriptor(metric)))
        .execute();
  }

  @Test
  public void registerMetric_fetchesStackdriverDefinition() throws Exception {
    // Stackdriver throws an Exception with the status message "ALREADY_EXISTS" when you try to
    // register a metric that's already been registered, so we fake one here.
    ByteArrayInputStream inputStream = new ByteArrayInputStream("".getBytes(UTF_8));
    HttpResponse response = GoogleJsonResponseExceptionHelper.createHttpResponse(400, inputStream);
    HttpResponseException.Builder httpResponseExceptionBuilder =
        new HttpResponseException.Builder(response);
    httpResponseExceptionBuilder.setStatusCode(400);
    httpResponseExceptionBuilder.setStatusMessage("ALREADY_EXISTS");
    GoogleJsonResponseException exception =
        new GoogleJsonResponseException(httpResponseExceptionBuilder, null);
    when(metricDescriptorCreate.execute()).thenThrow(exception);
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    writer.registerMetric(metric);

    verify(client.projects().metricDescriptors().get("metric")).execute();
  }

  @Test
  public void registerMetric_rethrowsException() throws Exception {
    ByteArrayInputStream inputStream = new ByteArrayInputStream("".getBytes(UTF_8));
    HttpResponse response = GoogleJsonResponseExceptionHelper.createHttpResponse(400, inputStream);
    HttpResponseException.Builder httpResponseExceptionBuilder =
        new HttpResponseException.Builder(response);
    httpResponseExceptionBuilder.setStatusCode(404);
    GoogleJsonResponseException exception =
        new GoogleJsonResponseException(httpResponseExceptionBuilder, null);
    when(metricDescriptorCreate.execute()).thenThrow(exception);
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    assertThrows(GoogleJsonResponseException.class, () -> writer.registerMetric(metric));
    assertThat(exception.getStatusCode()).isEqualTo(404);
  }

  @Test
  public void getEncodedTimeSeries_nullLabels_encodes() throws Exception {
    ByteArrayInputStream inputStream = new ByteArrayInputStream("".getBytes(UTF_8));
    HttpResponse response = GoogleJsonResponseExceptionHelper.createHttpResponse(400, inputStream);
    HttpResponseException.Builder httpResponseExceptionBuilder =
        new HttpResponseException.Builder(response);
    httpResponseExceptionBuilder.setStatusCode(400);
    httpResponseExceptionBuilder.setStatusMessage("ALREADY_EXISTS");
    GoogleJsonResponseException exception =
        new GoogleJsonResponseException(httpResponseExceptionBuilder, null);
    when(metricDescriptorCreate.execute()).thenThrow(exception);
    when(metricDescriptorGet.execute())
        .thenReturn(new MetricDescriptor().setName("foo").setLabels(null));
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);
    writer.registerMetric(metric);

    TimeSeries timeSeries =
        writer.getEncodedTimeSeries(
            MetricPoint.create(metric, ImmutableList.of("foo"), new Instant(1337), 10L));

    assertThat(timeSeries.getMetric().getLabels()).isEmpty();
  }

  @Test
  public void encodeMetricDescriptor_simpleMetric_encodes() {
    MetricDescriptor descriptor = StackdriverWriter.encodeMetricDescriptor(metric);

    assertThat(descriptor.getType()).isEqualTo("custom.googleapis.com/name");
    assertThat(descriptor.getValueType()).isEqualTo("INT64");
    assertThat(descriptor.getDescription()).isEqualTo("desc");
    assertThat(descriptor.getDisplayName()).isEqualTo("vdn");
    assertThat(descriptor.getLabels())
        .containsExactly(
            new com.google.api.services.monitoring.v3.model.LabelDescriptor()
                .setValueType("STRING")
                .setKey("label1")
                .setDescription("desc1"));
  }

  @Test
  public void encodeLabelDescriptors_simpleLabels_encodes() {
    ImmutableSet<LabelDescriptor> descriptors =
        ImmutableSet.of(
            LabelDescriptor.create("label1", "description1"),
            LabelDescriptor.create("label2", "description2"));

    ImmutableList<com.google.api.services.monitoring.v3.model.LabelDescriptor> encodedDescritors =
        StackdriverWriter.encodeLabelDescriptors(descriptors);

    assertThat(encodedDescritors)
        .containsExactly(
            new com.google.api.services.monitoring.v3.model.LabelDescriptor()
                .setValueType("STRING")
                .setKey("label1")
                .setDescription("description1"),
            new com.google.api.services.monitoring.v3.model.LabelDescriptor()
                .setValueType("STRING")
                .setKey("label2")
                .setDescription("description2"));
  }

  @Test
  public void getEncodedTimeSeries_cumulativeMetricPoint_ZeroInterval_encodesGreaterEndTime()
      throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);
    MetricPoint<Long> nativePoint =
        MetricPoint.create(
            metric, ImmutableList.of("foo"), new Instant(1337), new Instant(1337), 10L);

    TimeSeries timeSeries = writer.getEncodedTimeSeries(nativePoint);

    assertThat(timeSeries.getValueType()).isEqualTo("INT64");
    assertThat(timeSeries.getMetricKind()).isEqualTo("CUMULATIVE");
    List<Point> points = timeSeries.getPoints();
    assertThat(points).hasSize(1);
    Point point = points.get(0);
    assertThat(point.getValue().getInt64Value()).isEqualTo(10L);
    assertThat(point.getInterval().getStartTime()).isEqualTo("1970-01-01T00:00:01.337Z");
    assertThat(point.getInterval().getEndTime()).isEqualTo("1970-01-01T00:00:01.338Z");
  }

  @Test
  public void getEncodedTimeSeries_cumulativeMetricPoint_nonZeroInterval_encodesSameInterval()
      throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);
    MetricPoint<Long> nativePoint =
        MetricPoint.create(
            metric, ImmutableList.of("foo"), new Instant(1337), new Instant(1339), 10L);

    TimeSeries timeSeries = writer.getEncodedTimeSeries(nativePoint);

    assertThat(timeSeries.getValueType()).isEqualTo("INT64");
    assertThat(timeSeries.getMetricKind()).isEqualTo("CUMULATIVE");
    List<Point> points = timeSeries.getPoints();
    assertThat(points).hasSize(1);
    Point point = points.get(0);
    assertThat(point.getValue().getInt64Value()).isEqualTo(10L);
    assertThat(point.getInterval().getStartTime()).isEqualTo("1970-01-01T00:00:01.337Z");
    assertThat(point.getInterval().getEndTime()).isEqualTo("1970-01-01T00:00:01.339Z");
  }

  @Test
  public void getEncodedTimeSeries_gaugeMetricPoint_zeroInterval_encodesSameInterval()
      throws Exception {
    when(metric.getMetricSchema())
        .thenReturn(
            MetricSchema.create(
                "/name",
                "desc",
                "vdn",
                Kind.GAUGE,
                ImmutableSet.of(LabelDescriptor.create("label1", "desc1"))));
    // Store in an intermediate value, because Mockito hates when mocks are evaluated inside of
    // thenReturn() methods.
    MetricPoint<Long> testPoint =
        MetricPoint.create(metric, ImmutableList.of("foo"), new Instant(1337), 10L);
    when(metric.getTimestampedValues()).thenReturn(ImmutableList.of(testPoint));
    // Store in an intermediate value, because Mockito hates when mocks are evaluated inside of
    // thenReturn() methods.
    MetricDescriptor descriptor = StackdriverWriter.encodeMetricDescriptor(metric);
    when(metricDescriptorCreate.execute()).thenReturn(descriptor);
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);
    MetricPoint<Long> nativePoint =
        MetricPoint.create(
            metric, ImmutableList.of("foo"), new Instant(1337), new Instant(1337), 10L);

    TimeSeries timeSeries = writer.getEncodedTimeSeries(nativePoint);

    assertThat(timeSeries.getValueType()).isEqualTo("INT64");
    assertThat(timeSeries.getMetricKind()).isEqualTo("GAUGE");
    List<Point> points = timeSeries.getPoints();
    assertThat(points).hasSize(1);
    Point point = points.get(0);
    assertThat(point.getValue().getInt64Value()).isEqualTo(10L);
    assertThat(point.getInterval().getStartTime()).isEqualTo("1970-01-01T00:00:01.337Z");
    assertThat(point.getInterval().getEndTime()).isEqualTo("1970-01-01T00:00:01.337Z");
  }

  @Test
  public void getEncodedTimeSeries_booleanMetric_encodes() throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    MetricDescriptor boolDescriptor = StackdriverWriter.encodeMetricDescriptor(boolMetric);
    when(metricDescriptorCreate.execute()).thenReturn(boolDescriptor);
    MetricPoint<Boolean> nativePoint =
        MetricPoint.create(boolMetric, ImmutableList.of("foo"), new Instant(1337), true);

    TimeSeries timeSeries = writer.getEncodedTimeSeries(nativePoint);

    assertThat(timeSeries.getValueType()).isEqualTo("BOOL");
    assertThat(timeSeries.getMetricKind()).isEqualTo("GAUGE");
    List<Point> points = timeSeries.getPoints();
    assertThat(points).hasSize(1);
    Point point = points.get(0);
    assertThat(point.getValue().getBoolValue()).isEqualTo(true);
    assertThat(point.getInterval().getEndTime()).isEqualTo("1970-01-01T00:00:01.337Z");
    assertThat(point.getInterval().getStartTime()).isEqualTo("1970-01-01T00:00:01.337Z");
  }

  @Test
  public void getEncodedTimeSeries_distributionMetricCustomFitter_encodes() throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    MetricDescriptor descriptor = StackdriverWriter.encodeMetricDescriptor(distributionMetric);
    when(metricDescriptorCreate.execute()).thenReturn(descriptor);
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(5.0)));
    distribution.add(10.0, 5L);
    distribution.add(0.0, 5L);
    MetricPoint<Distribution> nativePoint =
        MetricPoint.create(
            distributionMetric, ImmutableList.of("foo"), new Instant(1337), distribution);

    TimeSeries timeSeries = writer.getEncodedTimeSeries(nativePoint);

    assertThat(timeSeries.getValueType()).isEqualTo("DISTRIBUTION");
    assertThat(timeSeries.getMetricKind()).isEqualTo("GAUGE");
    List<Point> points = timeSeries.getPoints();
    assertThat(points).hasSize(1);
    Point point = points.get(0);
    assertThat(point.getValue().getDistributionValue())
        .isEqualTo(
            new com.google.api.services.monitoring.v3.model.Distribution()
                .setMean(5.0)
                .setSumOfSquaredDeviation(250.0)
                .setCount(10L)
                .setBucketCounts(ImmutableList.of(5L, 5L))
                .setBucketOptions(
                    new BucketOptions()
                        .setExplicitBuckets(new Explicit().setBounds(ImmutableList.of(5.0)))));
    assertThat(point.getInterval().getEndTime()).isEqualTo("1970-01-01T00:00:01.337Z");
    assertThat(point.getInterval().getStartTime()).isEqualTo("1970-01-01T00:00:01.337Z");
  }

  @Test
  public void getEncodedTimeSeries_distributionMetricLinearFitter_encodes() throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    MetricDescriptor descriptor = StackdriverWriter.encodeMetricDescriptor(distributionMetric);
    when(metricDescriptorCreate.execute()).thenReturn(descriptor);
    MutableDistribution distribution = new MutableDistribution(LinearFitter.create(2, 5.0, 3.0));
    distribution.add(0.0, 1L);
    distribution.add(3.0, 2L);
    distribution.add(10.0, 5L);
    distribution.add(20.0, 5L);
    MetricPoint<Distribution> nativePoint =
        MetricPoint.create(
            distributionMetric, ImmutableList.of("foo"), new Instant(1337), distribution);


    TimeSeries timeSeries = writer.getEncodedTimeSeries(nativePoint);

    assertThat(timeSeries.getValueType()).isEqualTo("DISTRIBUTION");
    assertThat(timeSeries.getMetricKind()).isEqualTo("GAUGE");
    List<Point> points = timeSeries.getPoints();
    assertThat(points).hasSize(1);
    Point point = points.get(0);
    assertThat(point.getValue().getDistributionValue())
        .isEqualTo(
            new com.google.api.services.monitoring.v3.model.Distribution()
                .setMean(12.0)
                .setSumOfSquaredDeviation(646.0)
                .setCount(13L)
                .setBucketCounts(ImmutableList.of(1L, 2L, 5L, 5L))
                .setBucketOptions(
                    new BucketOptions()
                        .setLinearBuckets(
                            new Linear().setNumFiniteBuckets(2).setWidth(5.0).setOffset(3.0))));
    assertThat(point.getInterval().getEndTime()).isEqualTo("1970-01-01T00:00:01.337Z");
    assertThat(point.getInterval().getStartTime()).isEqualTo("1970-01-01T00:00:01.337Z");
  }

  @Test
  public void getEncodedTimeSeries_distributionMetricExponentialFitter_encodes() throws Exception {
    StackdriverWriter writer =
        new StackdriverWriter(client, PROJECT, MONITORED_RESOURCE, MAX_QPS, MAX_POINTS_PER_REQUEST);

    MetricDescriptor descriptor = StackdriverWriter.encodeMetricDescriptor(distributionMetric);
    when(metricDescriptorCreate.execute()).thenReturn(descriptor);
    MutableDistribution distribution =
        new MutableDistribution(ExponentialFitter.create(2, 3.0, 0.5));
    distribution.add(0.0, 1L);
    distribution.add(3.0, 2L);
    distribution.add(10.0, 5L);
    distribution.add(20.0, 5L);
    MetricPoint<Distribution> nativePoint =
        MetricPoint.create(
            distributionMetric, ImmutableList.of("foo"), new Instant(1337), distribution);

    TimeSeries timeSeries = writer.getEncodedTimeSeries(nativePoint);

    assertThat(timeSeries.getValueType()).isEqualTo("DISTRIBUTION");
    assertThat(timeSeries.getMetricKind()).isEqualTo("GAUGE");
    List<Point> points = timeSeries.getPoints();
    assertThat(points).hasSize(1);
    Point point = points.get(0);
    assertThat(point.getValue().getDistributionValue())
        .isEqualTo(
            new com.google.api.services.monitoring.v3.model.Distribution()
                .setMean(12.0)
                .setSumOfSquaredDeviation(646.0)
                .setCount(13L)
                .setBucketCounts(ImmutableList.of(1L, 0L, 2L, 10L))
                .setBucketOptions(
                    new BucketOptions()
                        .setExponentialBuckets(
                            new Exponential()
                                .setNumFiniteBuckets(2)
                                .setGrowthFactor(3.0)
                                .setScale(0.5))));
    assertThat(point.getInterval().getEndTime()).isEqualTo("1970-01-01T00:00:01.337Z");
    assertThat(point.getInterval().getStartTime()).isEqualTo("1970-01-01T00:00:01.337Z");
  }
}
