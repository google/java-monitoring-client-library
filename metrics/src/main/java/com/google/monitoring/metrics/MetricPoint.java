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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.joda.time.Instant;
import org.joda.time.Interval;

/**
 * Value type class to store a snapshot of a {@link Metric} value for a given label value tuple and
 * time {@link Interval}.
 */
@AutoValue
public abstract class MetricPoint<V> implements Comparable<MetricPoint<V>> {

  /**
   * Returns a new {@link MetricPoint} representing a value at a specific {@link Instant}.
   *
   * <p>Callers should insure that the count of {@code labelValues} matches the count of labels for
   * the given metric.
   */
  @VisibleForTesting
  public static <V> MetricPoint<V> create(
      Metric<V> metric, ImmutableList<String> labelValues, Instant timestamp, V value) {
    MetricsUtils.checkLabelValuesLength(metric, labelValues);

    return new AutoValue_MetricPoint<>(
        metric, labelValues, new Interval(timestamp, timestamp), value);
  }

  /**
   * Returns a new {@link MetricPoint} representing a value over an {@link Interval} from {@code
   * startTime} to {@code endTime}.
   *
   * <p>Callers should insure that the count of {@code labelValues} matches the count of labels for
   * the given metric.
   */
  @VisibleForTesting
  public static <V> MetricPoint<V> create(
      Metric<V> metric,
      ImmutableList<String> labelValues,
      Instant startTime,
      Instant endTime,
      V value) {
    MetricsUtils.checkLabelValuesLength(metric, labelValues);

    return new AutoValue_MetricPoint<>(
        metric, labelValues, new Interval(startTime, endTime), value);
  }

  public abstract Metric<V> metric();

  public abstract ImmutableList<String> labelValues();

  public abstract Interval interval();

  public abstract V value();

  @Override
  public int compareTo(MetricPoint<V> other) {
    int minLength = Math.min(this.labelValues().size(), other.labelValues().size());
    for (int index = 0; index < minLength; index++) {
      int comparisonResult =
          this.labelValues().get(index).compareTo(other.labelValues().get(index));
      if (comparisonResult != 0) {
        return comparisonResult;
      }
    }
    return Integer.compare(this.labelValues().size(), other.labelValues().size());
  }
}
