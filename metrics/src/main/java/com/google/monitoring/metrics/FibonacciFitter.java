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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSortedSet;

/**
 * Utility method to create a {@link CustomFitter} with intervals using the Fibonacci sequence.
 *
 * <p>A Fibonacci fitter is useful in situations where you want more precision on the low end than
 * an {@link ExponentialFitter} with exponent base 2 would provide without the hassle of dealing
 * with non-integer boundaries, such as would be created by an exponential fitter with a base of
 * less than 2. Fibonacci fitters are ideal for integer metrics that are bounded across a certain
 * range, e.g. integers between 1 and 1,000.
 *
 * <p>The interval boundaries are chosen as {@code (-inf, 0), [0, 1), [1, 2), [2, 3), [3, 5), [5,
 * 8), [8, 13)}, etc., up to {@code [fibonacciFloor(maxBucketSize), inf)}.
 */
public final class FibonacciFitter {

  /**
   * Returns a new {@link CustomFitter} with bounds corresponding to the Fibonacci sequence.
   *
   * @param maxBucketSize the maximum bucket size to create (rounded down to the nearest Fibonacci
   *     number)
   * @throws IllegalArgumentException if {@code maxBucketSize <= 0}
   */
  public static CustomFitter create(long maxBucketSize) {
    checkArgument(maxBucketSize > 0, "maxBucketSize must be greater than 0");

    ImmutableSortedSet.Builder<Double> boundaries = ImmutableSortedSet.naturalOrder();
    boundaries.add(Double.valueOf(0));
    long i = 1;
    long j = 2;
    long k = 3;
    while (i <= maxBucketSize) {
      boundaries.add(Double.valueOf(i));
      i = j;
      j = k;
      k = i + j;
    }

    return CustomFitter.create(boundaries.build());
  }

  private FibonacciFitter() {}
}
