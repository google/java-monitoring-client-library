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

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FibonacciFitter}. */
@RunWith(JUnit4.class)
public class FibonacciFitterTest {

  @Test
  public void testCreate_maxBucketSizeNegative_throwsException() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> FibonacciFitter.create(-1));
    assertThat(e).hasMessageThat().isEqualTo("maxBucketSize must be greater than 0");
  }

  @Test
  public void testCreate_maxBucketSizeZero_throwsException() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> FibonacciFitter.create(0));
    assertThat(e).hasMessageThat().isEqualTo("maxBucketSize must be greater than 0");
  }

  @Test
  public void testCreate_maxBucketSizeOne_createsTwoBoundaries() {
    assertThat(FibonacciFitter.create(1).boundaries()).containsExactly(0.0, 1.0).inOrder();
  }

  @Test
  public void testCreate_maxBucketSizeTwo_createsThreeBoundaries() {
    assertThat(FibonacciFitter.create(2).boundaries()).containsExactly(0.0, 1.0, 2.0).inOrder();
  }

  @Test
  public void testCreate_maxBucketSizeThree_createsFourBoundaries() {
    assertThat(FibonacciFitter.create(3).boundaries())
        .containsExactly(0.0, 1.0, 2.0, 3.0)
        .inOrder();
  }

  @Test
  public void testCreate_maxBucketSizeFour_createsFourBoundaries() {
    assertThat(FibonacciFitter.create(4).boundaries())
        .containsExactly(0.0, 1.0, 2.0, 3.0)
        .inOrder();
  }

  @Test
  public void testCreate_maxBucketSizeLarge_createsFibonacciSequenceBoundaries() {
    ImmutableList<Double> expectedBoundaries =
        ImmutableList.of(
            0.0, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 34.0, 55.0, 89.0, 144.0, 233.0, 377.0, 610.0,
            987.0);
    assertThat(FibonacciFitter.create(1000).boundaries())
        .containsExactlyElementsIn(expectedBoundaries)
        .inOrder();
  }
}
