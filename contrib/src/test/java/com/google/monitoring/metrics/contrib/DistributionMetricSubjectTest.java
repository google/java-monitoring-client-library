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
import static com.google.monitoring.metrics.contrib.DistributionMetricSubject.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.monitoring.metrics.EventMetric;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.MetricRegistryImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DistributionMetricSubjectTest {

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("species", "Sheep Species"),
          LabelDescriptor.create("color", "Sheep Color"));

  private static final EventMetric metric =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/test/event/sheep",
              "Sheep Latency",
              "sheeplatency",
              LABEL_DESCRIPTORS,
              EventMetric.DEFAULT_FITTER);

  @Before
  public void before() {
    metric.reset();
    metric.record(2.5, "Domestic", "Green");
    metric.record(10, "Bighorn", "Blue");
  }

  @Test
  public void testWrongNumberOfLabels_fails() {
    AssertionError e =
        assertThrows(
            AssertionError.class, () -> assertThat(metric).hasAnyValueForLabels("Domestic"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/event/sheep> has a value for labels <Domestic>."
                + " It has labeled values <[Bighorn:Blue =>"
                + " {[4.0..16.0)=1}, Domestic:Green => {[1.0..4.0)=1}]>");
  }

  @Test
  public void testDoesNotHaveWrongNumberOfLabels_succeeds() {
    assertThat(metric).doesNotHaveAnyValueForLabels("Domestic");
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
            "Not true that </test/event/sheep> has no value for labels <Domestic:Green>."
                + " It has a value of <{[1.0..4.0)=1}>");
  }

  @Test
  public void testUnexpectedValue_failure() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                assertThat(metric)
                    .hasAnyValueForLabels("Domestic", "Green")
                    .and()
                    .hasNoOtherValues());
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/event/sheep> has <no other nondefault values>."
                + " It has labeled values <[Bighorn:Blue =>"
                + " {[4.0..16.0)=1}, Domestic:Green => {[1.0..4.0)=1}]>");
  }

  @Test
  public void testExpectedDataSet_success() {
    metric.record(7.5, "Domestic", "Green");
    assertThat(metric).hasDataSetForLabels(ImmutableSet.of(2.5, 7.5), "Domestic", "Green");
  }

  @Test
  public void testExpectedDataSetsChained_success() {
    metric.record(7.5, "Domestic", "Green");
    assertThat(metric)
        .hasDataSetForLabels(ImmutableSet.of(2.5, 7.5), "Domestic", "Green")
        .and()
        .hasDataSetForLabels(ImmutableSet.of(10), "Bighorn", "Blue")
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testUnexpectedDataSet_failure() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                assertThat(metric)
                    .hasDataSetForLabels(ImmutableSet.of(2.5, 7.5), "Domestic", "Green"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/event/sheep> has a value of"
                + " {[1.0..4.0)=1,[4.0..16.0)=1} for labels <Domestic:Green>."
                + " It has a value of <{[1.0..4.0)=1}>");
  }

  @Test
  public void testNonExistentLabels_failure() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                assertThat(metric)
                    .hasDataSetForLabels(ImmutableSet.of(2.5, 7.5), "Domestic", "Blue"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/event/sheep> has a value for labels <Domestic:Blue>."
                + " It has labeled values <[Bighorn:Blue => {[4.0..16.0)=1},"
                + " Domestic:Green => {[1.0..4.0)=1}]>");
  }

  @Test
  public void testEmptyMetric_failure() {
    EventMetric emptyMetric =
        MetricRegistryImpl.getDefault()
            .newEventMetric(
                "/test/event/goat",
                "Sheep Latency",
                "sheeplatency",
                LABEL_DESCRIPTORS,
                EventMetric.DEFAULT_FITTER);
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                assertThat(emptyMetric)
                    .hasDataSetForLabels(ImmutableSet.of(2.5, 7.5), "Domestic", "Blue"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Not true that </test/event/goat> has a distribution for labels <Domestic:Blue>."
                + " It has <no values>");
  }
}
