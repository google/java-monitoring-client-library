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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;

/** Static store of metrics internal to this client library. */
final class MetricMetrics {

  /** A counter representing the total number of push intervals since the start of the process. */
  static final IncrementableMetric pushIntervals =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/metrics/push_intervals",
              "Count of push intervals.",
              "Push Intervals",
              ImmutableSet.of());
  private static final ImmutableSet<LabelDescriptor> LABELS =
      ImmutableSet.of(
          LabelDescriptor.create("kind", "Metric Kind"),
          LabelDescriptor.create("valueType", "Metric Value Type"));

  /**
   * A counter representing the total number of points pushed. Has {@link MetricSchema.Kind} and
   * Metric value classes as LABELS.
   */
  static final IncrementableMetric pushedPoints =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/metrics/points_pushed",
              "Count of points pushed to Monitoring API.",
              "Points Pushed",
              LABELS);

  /** A gauge representing a snapshot of the number of active timeseries being reported. */
  @SuppressWarnings("unused")
  private static final Metric<Long> timeseriesCount =
      MetricRegistryImpl.getDefault()
          .newGauge(
              "/metrics/timeseries_count",
              "Count of Timeseries being pushed to Monitoring API",
              "Timeseries Count",
              LABELS,
              () -> {
                HashMap<ImmutableList<String>, Long> timeseriesCount = new HashMap<>();

                for (Metric<?> metric : MetricRegistryImpl.getDefault().getRegisteredMetrics()) {
                  ImmutableList<String> key =
                      ImmutableList.of(
                          metric.getMetricSchema().kind().toString(),
                          metric.getValueClass().toString());

                  int cardinality = metric.getCardinality();
                  if (!timeseriesCount.containsKey(key)) {
                    timeseriesCount.put(key, (long) cardinality);
                  } else {
                    timeseriesCount.put(key, timeseriesCount.get(key) + cardinality);
                  }
                }

                return ImmutableMap.copyOf(timeseriesCount);
              },
              Long.class);

  private MetricMetrics() {}
}
