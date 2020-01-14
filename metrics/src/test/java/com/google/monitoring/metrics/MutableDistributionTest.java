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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link MutableDistribution} */
@RunWith(JUnit4.class)
public class MutableDistributionTest {

  private MutableDistribution distribution;
  @Before
  public void setUp() throws Exception {
    distribution = new MutableDistribution(CustomFitter.create(ImmutableSet.of(3.0, 5.0)));
  }

  @Test
  public void testAdd_oneValue() {
    distribution.add(5.0);

    assertThat(distribution.count()).isEqualTo(1);
    assertThat(distribution.mean()).isWithin(0.0).of(5.0);
    assertThat(distribution.sumOfSquaredDeviation()).isWithin(0.0).of(0);
    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(3.0), 0L)
                .put(Range.closedOpen(3.0, 5.0), 0L)
                .put(Range.atLeast(5.0), 1L)
                .build());
  }

  @Test
  public void testAdd_zero() {
    distribution.add(0.0);

    assertThat(distribution.count()).isEqualTo(1);
    assertThat(distribution.mean()).isWithin(0.0).of(0.0);
    assertThat(distribution.sumOfSquaredDeviation()).isWithin(0.0).of(0);
    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(3.0), 1L)
                .put(Range.closedOpen(3.0, 5.0), 0L)
                .put(Range.atLeast(5.0), 0L)
                .build());
  }

  @Test
  public void testAdd_multipleOfOneValue() {
    distribution.add(4.0, 2);

    assertThat(distribution.count()).isEqualTo(2);
    assertThat(distribution.mean()).isWithin(0.0).of(4.0);
    assertThat(distribution.sumOfSquaredDeviation()).isWithin(0.0).of(0);
    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(3.0), 0L)
                .put(Range.closedOpen(3.0, 5.0), 2L)
                .put(Range.atLeast(5.0), 0L)
                .build());
  }

  @Test
  public void testAdd_positiveThenNegativeValue() {
    distribution.add(2.0);
    distribution.add(-2.0);

    assertThat(distribution.count()).isEqualTo(2);
    assertThat(distribution.mean()).isWithin(0.0).of(0.0);
    assertThat(distribution.sumOfSquaredDeviation()).isWithin(0.0).of(8.0);
    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(3.0), 2L)
                .put(Range.closedOpen(3.0, 5.0), 0L)
                .put(Range.atLeast(5.0), 0L)
                .build());
  }

  @Test
  public void testAdd_wideRangeOfValues() {
    distribution.add(2.0);
    distribution.add(16.0);
    distribution.add(128.0, 5);
    distribution.add(1024.0, 0);

    assertThat(distribution.count()).isEqualTo(7);
    assertThat(distribution.mean()).isWithin(0.0).of(94.0);
    assertThat(distribution.sumOfSquaredDeviation()).isWithin(0.0).of(20328.0);
    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(3.0), 1L)
                .put(Range.closedOpen(3.0, 5.0), 0L)
                .put(Range.atLeast(5.0), 6L)
                .build());
  }

  @Test
  public void testAdd_negativeZero_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> distribution.add(Double.longBitsToDouble(0x80000000)));
    assertThat(thrown).hasMessageThat().contains("value must be finite, not NaN, and not -0.0");
  }

  @Test
  public void testAdd_NaN_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> distribution.add(Double.NaN));
    assertThat(thrown).hasMessageThat().contains("value must be finite, not NaN, and not -0.0");
  }

  @Test
  public void testAdd_positiveInfinity_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> distribution.add(Double.POSITIVE_INFINITY));
    assertThat(thrown).hasMessageThat().contains("value must be finite, not NaN, and not -0.0");
  }

  @Test
  public void testAdd_negativeInfinity_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> distribution.add(Double.NEGATIVE_INFINITY));
    assertThat(thrown).hasMessageThat().contains("value must be finite, not NaN, and not -0.0");
  }

  @Test
  public void testAdd_iteratedFloatingPointValues_hasLowAccumulatedError() {
    for (int i = 0; i < 500; i++) {
      distribution.add(1 / 3.0);
      distribution.add(1 / 7.0);
    }

    // Test for nine significant figures of accuracy.
    assertThat(distribution.mean()).isWithin(0.000000001).of(5.0 / 21.0);
    assertThat(distribution.sumOfSquaredDeviation())
        .isWithin(0.000000001)
        .of(1000 * 4.0 / (21.0 * 21.0));
  }

  @Test
  public void testAdd_fitterWithNoFiniteIntervals_underflowValue_returnsUnderflowInterval()
      throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(5.0)));

    distribution.add(3.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(5.0), 1L)
                .put(Range.atLeast(5.0), 0L)
                .build());
  }

  @Test
  public void testAdd_noFiniteIntervals_overflowValue_returnsOverflowInterval() throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(5.0)));

    distribution.add(10.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(5.0), 0L)
                .put(Range.atLeast(5.0), 1L)
                .build());
  }

  @Test
  public void testAdd_noFiniteIntervals_edgeValue_returnsOverflowInterval() throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(2.0)));

    distribution.add(2.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(2.0), 0L)
                .put(Range.atLeast(2.0), 1L)
                .build());
  }

  @Test
  public void testAdd_oneFiniteInterval_underflowValue_returnsUnderflowInterval() throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(1.0, 5.0)));

    distribution.add(0.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(1.0), 1L)
                .put(Range.closedOpen(1.0, 5.0), 0L)
                .put(Range.atLeast(5.0), 0L)
                .build());
  }

  @Test
  public void testAdd_oneFiniteInterval_overflowValue_returnsOverflowInterval() throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(1.0, 5.0)));

    distribution.add(10.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(1.0), 0L)
                .put(Range.closedOpen(1.0, 5.0), 0L)
                .put(Range.atLeast(5.0), 1L)
                .build());
  }

  @Test
  public void testAdd_oneFiniteInterval_inBoundsValue_returnsInBoundsInterval() throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(1.0, 5.0)));

    distribution.add(3.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(1.0), 0L)
                .put(Range.closedOpen(1.0, 5.0), 1L)
                .put(Range.atLeast(5.0), 0L)
                .build());
  }

  @Test
  public void testAdd_oneFiniteInterval_firstEdgeValue_returnsFiniteInterval() throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(1.0, 5.0)));

    distribution.add(1.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(1.0), 0L)
                .put(Range.closedOpen(1.0, 5.0), 1L)
                .put(Range.atLeast(5.0), 0L)
                .build());
  }

  @Test
  public void testAdd_oneFiniteInterval_secondEdgeValue_returnsOverflowInterval() throws Exception {
    MutableDistribution distribution =
        new MutableDistribution(CustomFitter.create(ImmutableSet.of(1.0, 5.0)));

    distribution.add(5.0);

    assertThat(distribution.intervalCounts())
        .isEqualTo(
            ImmutableRangeMap.<Double, Long>builder()
                .put(Range.lessThan(1.0), 0L)
                .put(Range.closedOpen(1.0, 5.0), 0L)
                .put(Range.atLeast(5.0), 1L)
                .build());
  }
}
