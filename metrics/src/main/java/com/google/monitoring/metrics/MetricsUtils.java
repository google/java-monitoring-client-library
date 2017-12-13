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

import com.google.common.collect.ImmutableList;
import java.util.concurrent.ConcurrentHashMap;

/** Static helper methods for the Metrics library. */
final class MetricsUtils {

  private static final Double NEGATIVE_ZERO = -0.0;
  private static final String LABEL_SIZE_ERROR =
      "The count of labelValues must be equal to the underlying Metric's count of labels.";

  /**
   * The below constants replicate the default initial capacity, load factor, and concurrency level
   * for {@link ConcurrentHashMap} as of Java SE 7. They are recorded here so that a {@link
   * com.google.common.util.concurrent.Striped} object can be constructed with a concurrency level
   * matching the default concurrency level of a {@link ConcurrentHashMap}.
   */
  private static final int HASHMAP_INITIAL_CAPACITY = 16;
  private static final float HASHMAP_LOAD_FACTOR = 0.75f;
  static final int DEFAULT_CONCURRENCY_LEVEL = 16;

  private MetricsUtils() {}

  /**
   * Check that the given {@code labelValues} match in count with the count of {@link
   * LabelDescriptor} instances on the given {@code metric}
   *
   * @throws IllegalArgumentException if there is a count mismatch.
   */
  static void checkLabelValuesLength(Metric<?> metric, String[] labelValues) {
    checkArgument(labelValues.length == metric.getMetricSchema().labels().size(), LABEL_SIZE_ERROR);
  }

  /**
   * Check that the given {@code labelValues} match in count with the count of {@link
   * LabelDescriptor} instances on the given {@code metric}
   *
   * @throws IllegalArgumentException if there is a count mismatch.
   */
  static void checkLabelValuesLength(Metric<?> metric, ImmutableList<String> labelValues) {
    checkArgument(labelValues.size() == metric.getMetricSchema().labels().size(), LABEL_SIZE_ERROR);
  }

  /** Check that the given double is not infinite, {@code NaN}, or {@code -0.0}. */
  static void checkDouble(double value) {
    checkArgument(
        !Double.isInfinite(value) && !Double.isNaN(value) && !NEGATIVE_ZERO.equals(value),
        "value must be finite, not NaN, and not -0.0");
  }

  static <K, V> ConcurrentHashMap<K, V> newConcurrentHashMap(int concurrencyLevel) {
    return new ConcurrentHashMap<>(HASHMAP_INITIAL_CAPACITY, HASHMAP_LOAD_FACTOR, concurrencyLevel);
  }
}
