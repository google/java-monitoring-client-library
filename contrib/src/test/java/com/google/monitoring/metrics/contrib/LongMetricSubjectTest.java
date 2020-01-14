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

import static com.google.common.truth.Truth.assertThat;
import static com.google.monitoring.metrics.contrib.LongMetricSubject.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.monitoring.metrics.IncrementableMetric;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.MetricRegistryImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LongMetricSubjectTest {

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("species", "Sheep Species"),
          LabelDescriptor.create("color", "Sheep Color"));

  private static final IncrementableMetric metric =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/test/incrementable/sheep",
              "Count of Sheep",
              "sheepcount",
              LABEL_DESCRIPTORS);

  @Before
  public void before() {
    metric.reset();
    metric.increment("Domestic", "Green");
    metric.incrementBy(2, "Bighorn", "Blue");
  }

  @Test
  public void testWrongNumberOfLabels_fails() {
    AssertionError e =
        assertThrows(
            AssertionError.class, () -> assertThat(metric).hasValueForLabels(1, "Domestic"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/incrementable/sheep> has a value for labels <Domestic>."
                + " It has labeled values <[Bighorn:Blue => 2, Domestic:Green => 1]>");
  }

  @Test
  public void testDoesNotHaveWrongNumberOfLabels_succeeds() {
    assertThat(metric).doesNotHaveAnyValueForLabels("Domestic");
  }

  @Test
  public void testHasValueForLabels_success() {
    assertThat(metric)
        .hasValueForLabels(1, "Domestic", "Green")
        .and()
        .hasValueForLabels(2, "Bighorn", "Blue")
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testHasAnyValueForLabels_success() {
    assertThat(metric)
        .hasAnyValueForLabels("Domestic", "Green")
        .and()
        .hasAnyValueForLabels("Bighorn", "Blue")
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testDoesNotHaveValueForLabels_success() {
    assertThat(metric).doesNotHaveAnyValueForLabels("Domestic", "Blue");
  }

  @Test
  public void testDoesNotHaveValueForLabels_failure() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () -> assertThat(metric).doesNotHaveAnyValueForLabels("Domestic", "Green"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/incrementable/sheep> has no value for labels <Domestic:Green>."
                + " It has a value of <1>");
  }

  @Test
  public void testWrongValue_failure() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () -> assertThat(metric).hasValueForLabels(2, "Domestic", "Green"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/incrementable/sheep> has a value of 2"
                + " for labels <Domestic:Green>. It has a value of <1>");
  }

  @Test
  public void testUnexpectedValue_failure() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                assertThat(metric)
                    .hasValueForLabels(1, "Domestic", "Green")
                    .and()
                    .hasNoOtherValues());
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/incrementable/sheep> has <no other nondefault values>."
                + " It has labeled values <[Bighorn:Blue => 2, Domestic:Green => 1]>");
  }
}
