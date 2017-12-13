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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ExponentialFitter}. */
@RunWith(JUnit4.class)
public class ExponentialFitterTest {
  @Test
  public void testCreateExponentialFitter_zeroNumIntervals_throwsException() throws Exception {
    IllegalArgumentException thrown =
        expectThrows(IllegalArgumentException.class, () -> ExponentialFitter.create(0, 3.0, 1.0));
    assertThat(thrown).hasMessageThat().contains("numFiniteIntervals must be greater than 0");
  }

  @Test
  public void testCreateExponentialFitter_negativeNumIntervals_throwsException() throws Exception {
    IllegalArgumentException thrown =
        expectThrows(IllegalArgumentException.class, () -> ExponentialFitter.create(-1, 3.0, 1.0));
    assertThat(thrown).hasMessageThat().contains("numFiniteIntervals must be greater than 0");
  }

  @Test
  public void testCreateExponentialFitter_invalidBase_throwsException() throws Exception {
    IllegalArgumentException thrown =
        expectThrows(IllegalArgumentException.class, () -> ExponentialFitter.create(3, 0.5, 1.0));
    assertThat(thrown).hasMessageThat().contains("base must be greater than 1");
  }

  @Test
  public void testCreateExponentialFitter_zeroScale_throwsException() throws Exception {
    IllegalArgumentException thrown =
        expectThrows(IllegalArgumentException.class, () -> ExponentialFitter.create(3, 2.0, 0.0));
    assertThat(thrown).hasMessageThat().contains("scale must not be 0");
  }

  @Test
  public void testCreateExponentialFitter_NanScale_throwsException() throws Exception {
    IllegalArgumentException thrown =
        expectThrows(
            IllegalArgumentException.class, () -> ExponentialFitter.create(3, 2.0, Double.NaN));
    assertThat(thrown).hasMessageThat().contains("value must be finite, not NaN, and not -0.0");
  }

  @Test
  public void testCreateExponentialFitter_hasCorrectBounds() {
    ExponentialFitter fitter = ExponentialFitter.create(3, 5.0, 2.0);

    assertThat(fitter.boundaries()).containsExactly(2.0, 10.0, 50.0, 250.0).inOrder();
  }
}
