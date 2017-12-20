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

package com.google.monitoring.metrics.contrib;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricPoint;
import javax.annotation.Nullable;

/**
 * Truth subject for {@link Long} {@link Metric} instances.
 *
 * <p>For use with the Google <a href="https://google.github.io/truth/">Truth</a> framework. Usage:
 *
 * <pre>  assertThat(myLongMetric)
 *       .hasValueForLabels(5, "label1", "label2", "label3")
 *       .and()
 *       .hasAnyValueForLabels("label1", "label2", "label4")
 *       .and()
 *       .hasNoOtherValues();
 *   assertThat(myLongMetric)
 *       .doesNotHaveAnyValueForLabels("label1", "label2");
 * </pre>
 *
 * <p>The assertions treat a value of 0 as no value at all. This is not how the data is actually
 * stored; zero is a valid value for incrementable metrics, and they do in fact have a value of zero
 * after they are reset. But it's difficult to write assertions about expected metric data when any
 * number of zero values can also be present, so they are screened out for convenience.
 */
public final class LongMetricSubject extends AbstractMetricSubject<Long, LongMetricSubject> {

  /**  Static shortcut method for {@link Long} metrics. */
  public static LongMetricSubject assertThat(@Nullable Metric<Long> metric) {
    return assertAbout(LongMetricSubject::new).that(metric);
  }

  private LongMetricSubject(FailureMetadata metadata, Metric<Long> actual) {
    super(metadata, actual);
  }

  /**
   * Asserts that the metric has a given value for the specified label values. This is a convenience
   * method that takes a long instead of a Long, for ease of use.
   */
  public And<LongMetricSubject> hasValueForLabels(long value, String... labels) {
    return hasValueForLabels(Long.valueOf(value), labels);
  }

  /**
   * Returns an indication to {@link AbstractMetricSubject#hasNoOtherValues} on whether a {@link
   * MetricPoint} has a non-zero value.
   */
  @Override
  protected boolean hasDefaultValue(MetricPoint<Long> metricPoint) {
    return metricPoint.value() == 0L;
  }
}
