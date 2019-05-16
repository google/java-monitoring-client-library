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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricPoint;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Base truth subject for asserting things about {@link Metric} instances.
 *
 * <p>For use with the Google <a href="https://google.github.io/truth/">Truth</a> framework.
 */
abstract class AbstractMetricSubject<T, S extends AbstractMetricSubject<T, S>>
    extends Subject<S, Metric<T>> {

  /** And chainer to allow fluent assertions. */
  public static class And<S extends AbstractMetricSubject<?, S>> {

    private final S subject;

    And(S subject) {
      this.subject = subject;
    }

    public S and() {
      return subject;
    }
  }

  @SuppressWarnings("unchecked")
  And<S> andChainer() {
    return new And<>((S) this);
  }

  /**
   * List of label value tuples about which an assertion has been made so far.
   *
   * <p>Used to track what tuples have been seen, in order to support hasNoOtherValues() assertions.
   */
  protected final Set<ImmutableList<String>> expectedNondefaultLabelTuples = new HashSet<>();

  /**
   * Function to convert a metric point to a nice string representation for use in error messages.
   */
  protected final Function<MetricPoint<T>, String> metricPointConverter =
      metricPoint ->
          String.format(
              "%s => %s",
              Joiner.on(':').join(metricPoint.labelValues()),
              getMessageRepresentation(metricPoint.value()));

  private final Metric<T> actual;

  protected AbstractMetricSubject(FailureMetadata metadata, Metric<T> actual) {
    super(metadata, checkNotNull(actual));
    this.actual = actual;
  }

  /**
   * Returns the string representation of the subject.
   *
   * <p>For metrics, it makes sense to use the metric name, as given in the schema.
   */
  @Override
  public String actualCustomStringRepresentation() {
    return actual.getMetricSchema().name();
  }

  /**
   * Asserts that the metric has a given value for the specified label values.
   *
   * @param value the value which the metric should have
   * @param labels the labels for which the value is being asserted; the number and order of labels
   *     should match the definition of the metric
   */
  public And<S> hasValueForLabels(T value, String... labels) {
    MetricPoint<T> metricPoint = findMetricPointForLabels(ImmutableList.copyOf(labels));
    if (metricPoint == null) {
      failWithBadResults(
          "has a value for labels",
          Joiner.on(':').join(labels),
          "has labeled values",
          Lists.transform(
              Ordering.<MetricPoint<T>>natural().sortedCopy(actual.getTimestampedValues()),
              metricPointConverter));
    }
    if (!metricPoint.value().equals(value)) {
      failWithBadResults(
          String.format("has a value of %s for labels", getMessageRepresentation(value)),
          Joiner.on(':').join(labels),
          "has a value of",
          getMessageRepresentation(metricPoint.value()));
    }
    expectedNondefaultLabelTuples.add(ImmutableList.copyOf(labels));
    return andChainer();
  }

  /**
   * Asserts that the metric has any (non-default) value for the specified label values.
   *
   * @param labels the labels for which the value is being asserted; the number and order of labels
   *     should match the definition of the metric
   */
  public And<S> hasAnyValueForLabels(String... labels) {
    MetricPoint<T> metricPoint = findMetricPointForLabels(ImmutableList.copyOf(labels));
    if (metricPoint == null) {
      failWithBadResults(
          "has a value for labels",
          Joiner.on(':').join(labels),
          "has labeled values",
          Lists.transform(
              Ordering.<MetricPoint<T>>natural().sortedCopy(actual.getTimestampedValues()),
              metricPointConverter));
    }
    if (hasDefaultValue(metricPoint)) {
      failWithBadResults(
          "has a non-default value for labels",
          Joiner.on(':').join(labels),
          "has a value of",
          getMessageRepresentation(metricPoint.value()));
    }
    expectedNondefaultLabelTuples.add(ImmutableList.copyOf(labels));
    return andChainer();
  }

  /** Asserts that the metric does not have a (non-default) value for the specified label values. */
  protected And<S> doesNotHaveAnyValueForLabels(String... labels) {
    MetricPoint<T> metricPoint = findMetricPointForLabels(ImmutableList.copyOf(labels));
    if (metricPoint != null) {
      failWithBadResults(
          "has no value for labels",
          Joiner.on(':').join(labels),
          "has a value of",
          getMessageRepresentation(metricPoint.value()));
    }
    return andChainer();
  }

  /**
   * Asserts that the metric has no (non-default) values other than those about which an assertion
   * has already been made.
   */
  public And<S> hasNoOtherValues() {
    for (MetricPoint<T> metricPoint : actual.getTimestampedValues()) {
      if (!expectedNondefaultLabelTuples.contains(metricPoint.labelValues())) {
        if (!hasDefaultValue(metricPoint)) {
          failWithBadResults(
              "has",
              "no other nondefault values",
              "has labeled values",
              Lists.transform(
                  Ordering.<MetricPoint<T>>natural().sortedCopy(actual.getTimestampedValues()),
                  metricPointConverter));
        }
        return andChainer();
      }
    }
    return andChainer();
  }

  private @Nullable MetricPoint<T> findMetricPointForLabels(ImmutableList<String> labels) {
    if (actual.getMetricSchema().labels().size() != labels.size()) {
      return null;
    }
    for (MetricPoint<T> metricPoint : actual.getTimestampedValues()) {
      if (metricPoint.labelValues().equals(labels)) {
        return metricPoint;
      }
    }
    return null;
  }

  /**
   * Returns a string representation of a metric point value, for use in error messages.
   *
   * <p>Subclass can override this method if the string needs extra processing.
   */
  protected String getMessageRepresentation(T value) {
    return String.valueOf(value);
  }

  /**
   * Returns true if the metric point has a non-default value.
   *
   * <p>This should be overridden by subclasses. E.g. for incrementable metrics, the method should
   * return true if the value is not zero, and so on.
   */
  protected abstract boolean hasDefaultValue(MetricPoint<T> metricPoint);
}
