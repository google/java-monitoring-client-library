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
 * Models a {@link DistributionFitter} with equally sized intervals.
 *
 * <p>The interval boundaries are defined by {@code width * i + offset} for {@code i} in {@code [0,
 * numFiniteIntervals}.
 *
 * <p>For example, a {@link LinearFitter} with {@code numFiniteIntervals=2, width=10, offset=5}
 * represents a histogram with intervals {@code (-inf, 5), [5, 15), [15, 25), [25, +inf)}.
 */
@AutoValue
public abstract class LinearFitter implements DistributionFitter {

  /**
   * Create a new {@link LinearFitter}.
   *
   * @param numFiniteIntervals the number of intervals, excluding the underflow and overflow
   *     intervals
   * @param width the width of each interval
   * @param offset the start value of the first interval
   * @throws IllegalArgumentException if {@code numFiniteIntervals <= 0} or {@code width <= 0}
   */
  public static LinearFitter create(int numFiniteIntervals, double width, double offset) {
    checkArgument(numFiniteIntervals > 0, "numFiniteIntervals must be greater than 0");
    checkArgument(width > 0, "width must be greater than 0");
    checkDouble(offset);

    ImmutableSortedSet.Builder<Double> boundaries = ImmutableSortedSet.naturalOrder();

    for (int i = 0; i < numFiniteIntervals + 1; i++) {
      boundaries.add(width * i + offset);
    }

    return new AutoValue_LinearFitter(width, offset, boundaries.build());
  }

  public abstract double width();

  public abstract double offset();

  @Override
  public abstract ImmutableSortedSet<Double> boundaries();
}
