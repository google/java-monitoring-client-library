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

package com.google.monitoring.metrics;

import static com.google.monitoring.metrics.MetricsUtils.DEFAULT_CONCURRENCY_LEVEL;
import static com.google.monitoring.metrics.MetricsUtils.newConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Striped;
import com.google.monitoring.metrics.MetricSchema.Kind;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import org.joda.time.Instant;

/**
 * A metric which stores {@link Distribution} values. The values are stateful and meant to be
 * updated incrementally via the {@link EventMetric#record(double, String...)} method.
 *
 * <p>This metric class is generally suitable for recording aggregations of data about a
 * quantitative aspect of an event. For example, this metric would be suitable for recording the
 * latency distribution for a request over the network.
 *
 * <p>The {@link MutableDistribution} values tracked by this metric can be reset with {@link
 * EventMetric#reset()}.
 */
public class EventMetric extends AbstractMetric<Distribution> {

  /**
   * Default {@link DistributionFitter} suitable for latency measurements.
   *
   * <p>The finite range of this fitter is from 1 to 4^16 (4294967296).
   */
  public static final DistributionFitter DEFAULT_FITTER = ExponentialFitter.create(16, 4.0, 1.0);

  private final ConcurrentHashMap<ImmutableList<String>, Instant> valueStartTimestamps =
      newConcurrentHashMap(DEFAULT_CONCURRENCY_LEVEL);
  private final ConcurrentHashMap<ImmutableList<String>, MutableDistribution> values =
      newConcurrentHashMap(DEFAULT_CONCURRENCY_LEVEL);

  private final DistributionFitter distributionFitter;

  /**
   * A fine-grained lock to ensure that {@code values} and {@code valueStartTimestamps} are modified
   * and read in a critical section. The initialization parameter is the concurrency level, set to
   * match the default concurrency level of {@link ConcurrentHashMap}.
   *
   * @see Striped
   */
  private final Striped<Lock> valueLocks = Striped.lock(DEFAULT_CONCURRENCY_LEVEL);

  EventMetric(
      String name,
      String description,
      String valueDisplayName,
      DistributionFitter distributionFitter,
      ImmutableSet<LabelDescriptor> labels) {
    super(name, description, valueDisplayName, Kind.CUMULATIVE, labels, Distribution.class);

    this.distributionFitter = distributionFitter;
  }

  @Override
  public final int getCardinality() {
    return values.size();
  }

  @Override
  public final ImmutableList<MetricPoint<Distribution>> getTimestampedValues() {
    return getTimestampedValues(Instant.now());
  }

  @VisibleForTesting
  ImmutableList<MetricPoint<Distribution>> getTimestampedValues(Instant endTimestamp) {
    ImmutableList.Builder<MetricPoint<Distribution>> timestampedValues =
        new ImmutableList.Builder<>();

    for (Entry<ImmutableList<String>, MutableDistribution> entry : values.entrySet()) {
      ImmutableList<String> labelValues = entry.getKey();
      Lock lock = valueLocks.get(labelValues);
      lock.lock();

      Instant startTimestamp;
      ImmutableDistribution distribution;
      try {
        startTimestamp = valueStartTimestamps.get(labelValues);
        distribution = ImmutableDistribution.copyOf(entry.getValue());
      } finally {
        lock.unlock();
      }

      // There is an opportunity for endTimestamp to be less than startTimestamp if
      // one of the modification methods is called on a value before the lock for that value is
      // acquired but after getTimestampedValues has been invoked. Just set endTimestamp equal to
      // startTimestamp if that happens.
      endTimestamp = Ordering.natural().max(startTimestamp, endTimestamp);

      timestampedValues.add(
          MetricPoint.create(this, labelValues, startTimestamp, endTimestamp, distribution));
    }

    return timestampedValues.build();
  }

  /**
   * Adds the given {@code sample} to the {@link Distribution} for the given {@code labelValues}.
   *
   * <p>If the metric is undefined for given label values, this method will autovivify the {@link
   * Distribution}.
   *
   * <p>The count of {@code labelValues} must be equal to the underlying metric's count of labels.
   */
  public void record(double sample, String... labelValues) {
    MetricsUtils.checkLabelValuesLength(this, labelValues);

    recordMultiple(sample, 1, Instant.now(), ImmutableList.copyOf(labelValues));
  }

  /**
   * Adds {@code count} of the given {@code sample} to the {@link Distribution} for the given {@code
   * labelValues}.
   *
   * <p>If the metric is undefined for given label values, this method will autovivify the {@link
   * Distribution}.
   *
   * <p>The count of {@code labelValues} must be equal to the underlying metric's count of labels.
   */
  public void record(double sample, int count, String... labelValues) {
    MetricsUtils.checkLabelValuesLength(this, labelValues);

    recordMultiple(sample, count, Instant.now(), ImmutableList.copyOf(labelValues));
  }

  @VisibleForTesting
  void recordMultiple(
      double sample, int count, Instant startTimestamp, ImmutableList<String> labelValues) {
    Lock lock = valueLocks.get(labelValues);
    lock.lock();

    try {
      values.computeIfAbsent(labelValues, k -> new MutableDistribution(distributionFitter));

      values.get(labelValues).add(sample, count);
      valueStartTimestamps.putIfAbsent(labelValues, startTimestamp);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Atomically resets the value and start timestamp of the metric for all label values.
   *
   * <p>This is useful if the metric is tracking values that are reset as part of a retrying
   * transaction, for example.
   */
  public void reset() {
    reset(Instant.now());
  }

  @VisibleForTesting
  final void reset(Instant startTime) {
    // Lock the entire set of values so that all existing values will have a consistent timestamp
    // after this call, without the possibility of interleaving with another reset() call.
    for (int i = 0; i < valueLocks.size(); i++) {
      valueLocks.getAt(i).lock();
    }

    try {
      for (ImmutableList<String> labelValues : values.keySet()) {
        this.values.put(labelValues, new MutableDistribution(distributionFitter));
        this.valueStartTimestamps.put(labelValues, startTime);
      }
    } finally {
      for (int i = 0; i < valueLocks.size(); i++) {
        valueLocks.getAt(i).unlock();
      }
    }
  }

  /**
   * Resets the value and start timestamp of the metric for the given label values.
   *
   * <p>This is useful if the metric is tracking a value that is reset as part of a retrying
   * transaction, for example.
   */
  public void reset(String... labelValues) {
    MetricsUtils.checkLabelValuesLength(this, labelValues);

    reset(Instant.now(), ImmutableList.copyOf(labelValues));
  }

  @VisibleForTesting
  final void reset(Instant startTimestamp, ImmutableList<String> labelValues) {
    Lock lock = valueLocks.get(labelValues);
    lock.lock();

    try {
      this.values.put(labelValues, new MutableDistribution(distributionFitter));
      this.valueStartTimestamps.put(labelValues, startTimestamp);
    } finally {
      lock.unlock();
    }
  }
}
