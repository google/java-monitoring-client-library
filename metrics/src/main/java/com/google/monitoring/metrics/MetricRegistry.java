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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** An interface to create and keep track of metrics. */
public interface MetricRegistry {

  /**
   * Returns a new Gauge metric.
   *
   * <p>The metric's values are computed at sample time via the supplied callback function. The
   * metric will be registered at the time of creation and collected for subsequent write intervals.
   *
   * <p>Since the metric's values are computed by a pre-defined callback function, this method only
   * returns a read-only {@link Metric} view.
   *
   * @param name name of the metric. Should be in the form of '/foo/bar'.
   * @param description human readable description of the metric. Must not be empty.
   * @param valueDisplayName human readable description of the metric's value type. Must not be
   *     empty.
   * @param labels list of the metric's labels. The labels (if there are any) must be of type
   *     STRING.
   * @param metricCallback {@link Supplier} to compute the on-demand values of the metric. The
   *     function should be lightweight to compute and must be thread-safe. The map keys, which are
   *     lists of strings, must match in quantity and order with the provided labels.
   * @param valueClass type hint to allow for compile-time encoding. Must match {@code V}.
   * @param <V> value type of the metric. Must be one of {@link Boolean}, {@link Double}, {@link
   *     Long}, or {@link String}.
   * @throws IllegalStateException if a metric of the same name is already registered.
   */
  <V> Metric<V> newGauge(
      String name,
      String description,
      String valueDisplayName,
      ImmutableSet<LabelDescriptor> labels,
      Supplier<ImmutableMap<ImmutableList<String>, V>> metricCallback,
      Class<V> valueClass);

  /**
   * Returns a new {@link SettableMetric}.
   *
   * <p>The metric's value is stateful and can be set to different values over time.
   *
   * <p>The metric is thread-safe.
   *
   * <p>The metric will be registered at the time of creation and collected for subsequent write
   * intervals.
   *
   * @param name name of the metric. Should be in the form of '/foo/bar'.
   * @param description human readable description of the metric.
   * @param valueDisplayName human readable description of the metric's value type.
   * @param labels list of the metric's labels. The labels (if there are any) must be of type
   *     STRING.
   * @param valueClass type hint to allow for compile-time encoding. Must match {@code V}.
   * @param <V> value type of the metric. Must be one of {@link Boolean}, {@link Double}, {@link
   *     Long}, or {@link String}.
   * @throws IllegalStateException if a metric of the same name is already registered.
   */
  <V> SettableMetric<V> newSettableMetric(
      String name,
      String description,
      String valueDisplayName,
      ImmutableSet<LabelDescriptor> labels,
      Class<V> valueClass);

  /**
   * Returns a new {@link IncrementableMetric}.
   *
   * <p>The metric's values are {@link Long}, and can be incremented, and decremented. The metric is
   * thread-safe. The metric will be registered at the time of creation and collected for subsequent
   * write intervals.
   *
   * <p>This metric type is generally intended for monotonically increasing values, although the
   * metric can in fact be incremented by negative numbers.
   *
   * @param name name of the metric. Should be in the form of '/foo/bar'.
   * @param description human readable description of the metric.
   * @param valueDisplayName human readable description of the metric's value type.
   * @param labels list of the metric's labels. The labels (if there are any) must be of type
   *     STRING.
   * @throws IllegalStateException if a metric of the same name is already registered.
   */
  IncrementableMetric newIncrementableMetric(
      String name,
      String description,
      String valueDisplayName,
      ImmutableSet<LabelDescriptor> labels);

  /**
   * Returns a new {@link EventMetric}.
   *
   * <p>This metric type is intended for recording aspects of an "event" -- things like latency or
   * payload size.
   *
   * <p>The metric's values are {@link Distribution} instances which are updated via {@link
   * EventMetric#record(double, String...)}.
   *
   * <p>The metric is thread-safe. The metric will be registered at the time of creation and
   * collected for subsequent write intervals.
   *
   * @param name name of the metric. Should be in the form of '/foo/bar'.
   * @param description human readable description of the metric.
   * @param valueDisplayName human readable description of the metric's value type.
   * @param labels list of the metric's labels.
   * @param distributionFitter fit to apply to the underlying {@link Distribution} instances of this
   *     metric.
   * @throws IllegalStateException if a metric of the same name is already registered.
   */
  EventMetric newEventMetric(
      String name,
      String description,
      String valueDisplayName,
      ImmutableSet<LabelDescriptor> labels,
      DistributionFitter distributionFitter);

  /**
   * Fetches a snapshot of the currently registered metrics
   *
   * <p>Users who wish to manually sample and write metrics without relying on the scheduled {@link
   * MetricReporter} can use this method to gather the list of metrics to report.
   */
  ImmutableList<Metric<?>> getRegisteredMetrics();
}
