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

import com.google.common.collect.ImmutableSortedSet;

/**
 * A companion interface to {@link Distribution} which fits samples to a histogram in order to
 * estimate the probability density function (PDF) of the {@link Distribution}.
 *
 * <p>The fitter models the histogram with a set of finite boundaries. The closed-open interval
 * [a,b) between two consecutive boundaries represents the domain of permissible values in that
 * interval. The values less than the first boundary are in the underflow interval of (-inf, a) and
 * values greater or equal to the last boundary in the array are in the overflow interval of [a,
 * inf).
 */
public interface DistributionFitter {

  /** Returns a sorted set of the boundaries modeled by this {@link DistributionFitter}. */
  ImmutableSortedSet<Double> boundaries();
}
