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

import com.google.common.base.Joiner;
import com.google.common.collect.BoundType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.truth.FailureMetadata;
import com.google.monitoring.metrics.Distribution;
import com.google.monitoring.metrics.ImmutableDistribution;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricPoint;
import com.google.monitoring.metrics.MutableDistribution;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Truth subject for {@link Distribution} {@link Metric} instances.
 *
 * <p>For use with the Google <a href="https://google.github.io/truth/">Truth</a> framework. Usage:
 *
 * <pre>  assertThat(myDistributionMetric)
 *       .hasAnyValueForLabels("label1", "label2", "label3")
 *       .and()
 *       .hasNoOtherValues();
 *   assertThat(myDistributionMetric)
 *       .doesNotHaveAnyValueForLabels("label1", "label2");
 *   assertThat(myDistributionMetric)
 *       .hasDataSetForLabels(ImmutableSet.of(data1, data2, data3), "label1", "label2");
 * </pre>
 *
 * <p>The assertions treat an empty distribution as no value at all. This is not how the data is
 * actually stored; event metrics do in fact have an empty distribution after they are reset. But
 * it's difficult to write assertions about expected metric data when any number of empty
 * distributions can also be present, so they are screened out for convenience.
 */
public final class DistributionMetricSubject
    extends AbstractMetricSubject<Distribution, DistributionMetricSubject> {

  /** Static shortcut method for {@link Distribution} {@link Metric} objects. */
  public static DistributionMetricSubject assertThat(@Nullable Metric<Distribution> metric) {
    return assertAbout(DistributionMetricSubject::new).that(metric);
  }

  private DistributionMetricSubject(FailureMetadata metadata, Metric<Distribution> actual) {
    super(metadata, actual);
  }

  /**
   * Returns an indication to {@link AbstractMetricSubject#hasNoOtherValues} on whether a {@link
   * MetricPoint} has a non-empty distribution.
   */
  @Override
  protected boolean hasDefaultValue(MetricPoint<Distribution> metricPoint) {
    return metricPoint.value().count() == 0;
  }

  /** Returns an appropriate string representation of a metric value for use in error messages. */
  @Override
  protected String getMessageRepresentation(Distribution distribution) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<Range<Double>, Long> entry :
        distribution.intervalCounts().asMapOfRanges().entrySet()) {
      if (entry.getValue() != 0L) {
        if (first) {
          first = false;
        } else {
          sb.append(',');
        }
        if (entry.getKey().hasLowerBound()) {
          sb.append((entry.getKey().lowerBoundType() == BoundType.CLOSED) ? '[' : '(');
          sb.append(entry.getKey().lowerEndpoint());
        }
        sb.append("..");
        if (entry.getKey().hasUpperBound()) {
          sb.append(entry.getKey().upperEndpoint());
          sb.append((entry.getKey().upperBoundType() == BoundType.CLOSED) ? ']' : ')');
        }
        sb.append('=');
        sb.append(entry.getValue());
      }
    }
    sb.append('}');
    return sb.toString();
  }

  /**
   * Asserts that the distribution for the given label can be constructed from the given data set.
   *
   * <p>Note that this only tests that the distribution has the same binned histogram, along with
   * the same mean, and sum of squared deviation as it would if it had recorded the specified data
   * points. It could have in fact collected different data points that resulted in the same
   * distribution, but that information is lost to us and cannot be tested.
   */
  public And<DistributionMetricSubject> hasDataSetForLabels(
      ImmutableSet<? extends Number> dataSet, String... labels) {
    ImmutableList<MetricPoint<Distribution>> metricPoints = actual().getTimestampedValues();
    if (metricPoints.isEmpty()) {
      failWithBadResults(
          "has a distribution for labels", Joiner.on(':').join(labels), "has", "no values");
    }
    MutableDistribution targetDistribution =
        new MutableDistribution(metricPoints.get(0).value().distributionFitter());
    dataSet.forEach(data -> targetDistribution.add(data.doubleValue()));
    return hasValueForLabels(ImmutableDistribution.copyOf(targetDistribution), labels);
  }
}
