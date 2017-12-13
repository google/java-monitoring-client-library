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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.monitoring.metrics.MetricsUtils.DEFAULT_CONCURRENCY_LEVEL;
import static com.google.monitoring.metrics.MetricsUtils.newConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.common.util.concurrent.Striped;
import com.google.monitoring.metrics.MetricSchema.Kind;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import javax.annotation.concurrent.ThreadSafe;
import org.joda.time.Instant;

/**
 * A metric which stores Long values. It is stateful and can be changed in increments.
 *
 * <p>This metric is generally suitable for counters, such as requests served or errors generated.
 *
 * <p>The start of the {@link MetricPoint#interval()} of values of instances of this metric will be
 * set to the time that the metric was first set or last {@link #reset()}.
 */
@ThreadSafe
public final class Counter extends AbstractMetric<Long>
    implements SettableMetric<Long>, IncrementableMetric {

  /**
   * A map of the {@link Counter} values, with a list of label values as the keys.
   *
   * <p>This should be modified in a critical section with {@code valueStartTimestamps} so that the
   * values are in sync.
   */
  private final AtomicLongMap<ImmutableList<String>> values = AtomicLongMap.create();

  /**
   * A map of the {@link Instant} that each value was created, with a list of label values as the
   * keys. The start timestamp (as part of the {@link MetricPoint#interval()} can be used by
   * implementations of {@link MetricWriter} to encode resets of monotonic counters.
   */
  private final ConcurrentHashMap<ImmutableList<String>, Instant> valueStartTimestamps =
      newConcurrentHashMap(DEFAULT_CONCURRENCY_LEVEL);

  /**
   * A fine-grained lock to ensure that {@code values} and {@code valueStartTimestamps} are modified
   * and read in a critical section. The initialization parameter is the concurrency level, set to
   * match the default concurrency level of {@link ConcurrentHashMap}.
   *
   * @see Striped
   */
  private final Striped<Lock> valueLocks = Striped.lock(DEFAULT_CONCURRENCY_LEVEL);

  /**
   * Constructs a new Counter.
   *
   * <p>Note that the order of the labels is significant.
   */
  Counter(
      String name,
      String description,
      String valueDisplayName,
      ImmutableSet<LabelDescriptor> labels) {
    super(name, description, valueDisplayName, Kind.CUMULATIVE, labels, Long.class);
  }

  @VisibleForTesting
  void incrementBy(long offset, Instant startTimestamp, ImmutableList<String> labelValues) {
    Lock lock = valueLocks.get(labelValues);
    lock.lock();

    try {
      values.addAndGet(labelValues, offset);
      valueStartTimestamps.putIfAbsent(labelValues, startTimestamp);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final void incrementBy(long offset, String... labelValues) {
    MetricsUtils.checkLabelValuesLength(this, labelValues);
    checkArgument(offset >= 0, "The offset provided must be non-negative");

    incrementBy(offset, Instant.now(), ImmutableList.copyOf(labelValues));
  }

  @Override
  public final void increment(String... labelValues) {
    MetricsUtils.checkLabelValuesLength(this, labelValues);

    incrementBy(1L, Instant.now(), ImmutableList.copyOf(labelValues));
  }

  /**
   * Returns a snapshot of the metric's values. The timestamp of each {@link MetricPoint} will be
   * the last modification time for that tuple of label values.
   */
  @Override
  public final ImmutableList<MetricPoint<Long>> getTimestampedValues() {
    return getTimestampedValues(Instant.now());
  }

  @Override
  public final int getCardinality() {
    return values.size();
  }

  @VisibleForTesting
  final ImmutableList<MetricPoint<Long>> getTimestampedValues(Instant endTimestamp) {
    ImmutableList.Builder<MetricPoint<Long>> timestampedValues = new ImmutableList.Builder<>();
    for (Entry<ImmutableList<String>, Long> entry : values.asMap().entrySet()) {
      ImmutableList<String> labelValues = entry.getKey();
      valueLocks.get(labelValues).lock();

      Instant startTimestamp;
      try {
        startTimestamp = valueStartTimestamps.get(labelValues);
      } finally {
        valueLocks.get(labelValues).unlock();
      }

      // There is an opportunity for endTimestamp to be less than startTimestamp if
      // one of the modification methods is called on a value before the lock for that value is
      // acquired but after getTimestampedValues has been invoked. Just set endTimestamp equal to
      // startTimestamp if that happens.
      endTimestamp = Ordering.natural().max(startTimestamp, endTimestamp);

      timestampedValues.add(
          MetricPoint.create(this, labelValues, startTimestamp, endTimestamp, entry.getValue()));

    }
    return timestampedValues.build();
  }

  @VisibleForTesting
  final void set(Long value, Instant startTimestamp, ImmutableList<String> labelValues) {
    Lock lock = valueLocks.get(labelValues);
    lock.lock();

    try {
      this.values.put(labelValues, value);
      valueStartTimestamps.putIfAbsent(labelValues, startTimestamp);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final void set(Long value, String... labelValues) {
    MetricsUtils.checkLabelValuesLength(this, labelValues);

    set(value, Instant.now(), ImmutableList.copyOf(labelValues));
  }

  @VisibleForTesting
  final void reset(Instant startTimestamp) {
    // Lock the entire set of values so that all existing values will have a consistent timestamp
    // after this call, without the possibility of interleaving with another reset() call.
    for (int i = 0; i < valueLocks.size(); i++) {
      valueLocks.getAt(i).lock();
    }

    try {
      for (ImmutableList<String> labelValues : values.asMap().keySet()) {
        this.values.put(labelValues, 0);
        this.valueStartTimestamps.put(labelValues, startTimestamp);
      }
    } finally {
      for (int i = 0; i < valueLocks.size(); i++) {
        valueLocks.getAt(i).unlock();
      }
    }
  }

  @Override
  public final void reset() {
    reset(Instant.now());
  }

  @VisibleForTesting
  final void reset(Instant startTimestamp, ImmutableList<String> labelValues) {
    Lock lock = valueLocks.get(labelValues);
    lock.lock();

    try {
      this.values.put(labelValues, 0);
      this.valueStartTimestamps.put(labelValues, startTimestamp);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final void reset(String... labelValues) {
    MetricsUtils.checkLabelValuesLength(this, labelValues);

    reset(Instant.now(), ImmutableList.copyOf(labelValues));
  }
}
