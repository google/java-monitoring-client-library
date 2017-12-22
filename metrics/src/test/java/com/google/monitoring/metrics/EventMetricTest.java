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

import static com.google.common.truth.Truth.assertThat;
import static com.google.monitoring.metrics.JUnitBackports.expectThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link EventMetric}.
 */
@RunWith(JUnit4.class)
public class EventMetricTest {

  private final DistributionFitter distributionFitter = CustomFitter.create(ImmutableSet.of(5.0));
  private EventMetric metric;

  @Before
  public void setUp() {
    metric =
        new EventMetric(
            "/metric",
            "description",
            "vdn",
            distributionFitter,
            ImmutableSet.of(LabelDescriptor.create("label1", "bar")));
  }

  @Test
  public void testGetCardinality_reflectsCurrentCardinality() {
    assertThat(metric.getCardinality()).isEqualTo(0);

    metric.record(1.0, "foo");

    assertThat(metric.getCardinality()).isEqualTo(1);

    metric.record(1.0, "bar");

    assertThat(metric.getCardinality()).isEqualTo(2);

    metric.record(1.0, "foo");

    assertThat(metric.getCardinality()).isEqualTo(2);
  }

  @Test
  public void testIncrementBy_wrongLabelValueCount_throwsException() {
    IllegalArgumentException thrown =
        expectThrows(IllegalArgumentException.class, () -> metric.record(1.0, "blah", "blah"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "The count of labelValues must be equal to the underlying Metric's count of labels.");
  }

  @Test
  public void testRecord_updatesDistribution() {
    assertThat(metric.getTimestampedValues()).isEmpty();

    metric.recordMultiple(1.0, 1, Instant.ofEpochMilli(1337), ImmutableList.of("test_value1"));

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1338)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("test_value1"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    1.0,
                    0.0,
                    1L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 1L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)));

    metric.record(10.0, "test_value1");

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1338)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("test_value1"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    5.5,
                    40.5,
                    2L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 1L)
                        .put(Range.atLeast(5.0), 1L)
                        .build(),
                    distributionFitter)));
  }

  @Test
  public void testRecord_multipleValues_updatesDistributions() {
    assertThat(metric.getTimestampedValues()).isEmpty();

    metric.recordMultiple(1.0, 3, Instant.ofEpochMilli(1337), ImmutableList.of("test_value1"));

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1338)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("test_value1"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    1.0,
                    0,
                    3L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 3L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)));

    metric.recordMultiple(2.0, 5, Instant.ofEpochMilli(1337), ImmutableList.of("test_value1"));
    metric.recordMultiple(7.0, 10, Instant.ofEpochMilli(1337), ImmutableList.of("test_value2"));

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1338)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("test_value1"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    1.625,
                    1.875,
                    8L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 8L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)),
            MetricPoint.create(
                metric,
                ImmutableList.of("test_value2"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    7.0,
                    0,
                    10L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 0L)
                        .put(Range.atLeast(5.0), 10L)
                        .build(),
                    distributionFitter)));
  }

  @Test
  public void testResetAll_resetsAllValuesAndStartTimestamps() {
    metric.recordMultiple(3.0, 1, Instant.ofEpochMilli(1336), ImmutableList.of("foo"));
    metric.recordMultiple(5.0, 1, Instant.ofEpochMilli(1337), ImmutableList.of("moo"));

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1338)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("foo"),
                Instant.ofEpochMilli(1336),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    3.0,
                    0.0,
                    1L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 1L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)),
            MetricPoint.create(
                metric,
                ImmutableList.of("moo"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    5.0,
                    0,
                    1L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 0L)
                        .put(Range.atLeast(5.0), 1L)
                        .build(),
                    distributionFitter)));

    metric.reset(Instant.ofEpochMilli(1339));

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1340)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("foo"),
                Instant.ofEpochMilli(1339),
                Instant.ofEpochMilli(1340),
                ImmutableDistribution.create(
                    0.0,
                    0.0,
                    0L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 0L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)),
            MetricPoint.create(
                metric,
                ImmutableList.of("moo"),
                Instant.ofEpochMilli(1339),
                Instant.ofEpochMilli(1340),
                ImmutableDistribution.create(
                    0.0,
                    0,
                    0L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 0L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)));
  }

  @Test
  public void testReset_resetsValueAndStartTimestamp() {
    metric.recordMultiple(3.0, 1, Instant.ofEpochMilli(1336), ImmutableList.of("foo"));
    metric.recordMultiple(5.0, 1, Instant.ofEpochMilli(1337), ImmutableList.of("moo"));

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1338)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("foo"),
                Instant.ofEpochMilli(1336),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    3.0,
                    0.0,
                    1L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 1L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)),
            MetricPoint.create(
                metric,
                ImmutableList.of("moo"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1338),
                ImmutableDistribution.create(
                    5.0,
                    0,
                    1L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 0L)
                        .put(Range.atLeast(5.0), 1L)
                        .build(),
                    distributionFitter)));

    metric.reset(Instant.ofEpochMilli(1339), ImmutableList.of("foo"));

    assertThat(metric.getTimestampedValues(Instant.ofEpochMilli(1340)))
        .containsExactly(
            MetricPoint.create(
                metric,
                ImmutableList.of("foo"),
                Instant.ofEpochMilli(1339),
                Instant.ofEpochMilli(1340),
                ImmutableDistribution.create(
                    0.0,
                    0.0,
                    0L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 0L)
                        .put(Range.atLeast(5.0), 0L)
                        .build(),
                    distributionFitter)),
            MetricPoint.create(
                metric,
                ImmutableList.of("moo"),
                Instant.ofEpochMilli(1337),
                Instant.ofEpochMilli(1340),
                ImmutableDistribution.create(
                    5.0,
                    0,
                    1L,
                    ImmutableRangeMap.<Double, Long>builder()
                        .put(Range.lessThan(5.0), 0L)
                        .put(Range.atLeast(5.0), 1L)
                        .build(),
                    distributionFitter)));
  }
}
