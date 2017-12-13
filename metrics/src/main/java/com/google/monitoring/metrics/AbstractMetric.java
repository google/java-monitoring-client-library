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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.monitoring.metrics.MetricSchema.Kind;

abstract class AbstractMetric<V> implements Metric<V> {

  private final Class<V> valueClass;
  private final MetricSchema metricSchema;

  AbstractMetric(
      String name,
      String description,
      String valueDisplayName,
      Kind kind,
      ImmutableSet<LabelDescriptor> labels,
      Class<V> valueClass) {
    this.metricSchema = MetricSchema.create(name, description, valueDisplayName, kind, labels);
    this.valueClass = valueClass;
  }

  /** Returns the schema of this metric. */
  @Override
  public final MetricSchema getMetricSchema() {
    return metricSchema;
  }

  /**
   * Returns the type for the value of this metric, which would otherwise be erased at compile-time.
   * This is useful for implementors of {@link MetricWriter}.
   */
  @Override
  public final Class<V> getValueClass() {
    return valueClass;
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("valueClass", valueClass)
        .add("schema", metricSchema)
        .toString();
  }
}
