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
import static com.google.monitoring.metrics.MetricsUtils.checkDouble;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSortedSet;

/**
 * Models a {@link DistributionFitter} with intervals of exponentially increasing size.
 *
 * <p>The interval boundaries are defined by {@code scale * Math.pow(base, i)} for {@code i} in
 * {@code [0, numFiniteIntervals]}.
 *
 * <p>For example, an {@link ExponentialFitter} with {@code numFiniteIntervals=3, base=4.0,
 * scale=1.5} represents a histogram with intervals {@code (-inf, 1.5), [1.5, 6), [6, 24), [24, 96),
 * [96, +inf)}.
 */
@AutoValue
public abstract class ExponentialFitter implements DistributionFitter {

  /**
   * Create a new {@link ExponentialFitter}.
   *
   * @param numFiniteIntervals the number of intervals, excluding the underflow and overflow
   *     intervals
   * @param base the base of the exponent
   * @param scale a multiplicative factor for the exponential function
   * @throws IllegalArgumentException if {@code numFiniteIntervals <= 0}, {@code width <= 0} or
   *     {@code base <= 1}
   */
  public static ExponentialFitter create(int numFiniteIntervals, double base, double scale) {
    checkArgument(numFiniteIntervals > 0, "numFiniteIntervals must be greater than 0");
    checkArgument(scale != 0, "scale must not be 0");
    checkArgument(base > 1, "base must be greater than 1");
    checkDouble(base);
    checkDouble(scale);

    ImmutableSortedSet.Builder<Double> boundaries = ImmutableSortedSet.naturalOrder();

    for (int i = 0; i < numFiniteIntervals + 1; i++) {
      boundaries.add(scale * Math.pow(base, i));
    }

    return new AutoValue_ExponentialFitter(base, scale, boundaries.build());
  }

  public abstract double base();

  public abstract double scale();

  @Override
  public abstract ImmutableSortedSet<Double> boundaries();
}
