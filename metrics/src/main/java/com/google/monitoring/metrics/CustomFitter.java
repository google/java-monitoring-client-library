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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;

/**
 * Models a {@link DistributionFitter} with arbitrary sized intervals.
 *
 * <p>If only only one boundary is provided, then the fitter will consist of an overflow and
 * underflow interval separated by that boundary.
 */
@AutoValue
public abstract class CustomFitter implements DistributionFitter {

  /**
   * Create a new {@link CustomFitter} with the given interval boundaries.
   *
   * @param boundaries is a sorted list of interval boundaries
   * @throws IllegalArgumentException if {@code boundaries} is empty or not sorted in ascending
   *     order, or if a value in the set is infinite, {@code NaN}, or {@code -0.0}.
   */
  public static CustomFitter create(ImmutableSet<Double> boundaries) {
    checkArgument(boundaries.size() > 0, "boundaries must not be empty");
    checkArgument(Ordering.natural().isOrdered(boundaries), "boundaries must be sorted");
    for (Double d : boundaries) {
      checkDouble(d);
    }

    return new AutoValue_CustomFitter(ImmutableSortedSet.copyOf(boundaries));
  }

  @Override
  public abstract ImmutableSortedSet<Double> boundaries();
}
