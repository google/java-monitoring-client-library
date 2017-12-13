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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableRangeMap;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An immutable {@link Distribution}. Instances of this class can used to create {@link MetricPoint}
 * instances, and should be used when exporting values to a {@link MetricWriter}.
 *
 * @see MutableDistribution
 */
@ThreadSafe
@AutoValue
public abstract class ImmutableDistribution implements Distribution {

  public static ImmutableDistribution copyOf(Distribution distribution) {
    return new AutoValue_ImmutableDistribution(
        distribution.mean(),
        distribution.sumOfSquaredDeviation(),
        distribution.count(),
        distribution.intervalCounts(),
        distribution.distributionFitter());
  }

  @VisibleForTesting
  static ImmutableDistribution create(
      double mean,
      double sumOfSquaredDeviation,
      long count,
      ImmutableRangeMap<Double, Long> intervalCounts,
      DistributionFitter distributionFitter) {
    checkDouble(mean);
    checkDouble(sumOfSquaredDeviation);
    checkArgument(count >= 0);

    return new AutoValue_ImmutableDistribution(
        mean, sumOfSquaredDeviation, count, intervalCounts, distributionFitter);
  }

  @Override
  public abstract double mean();

  @Override
  public abstract double sumOfSquaredDeviation();

  @Override
  public abstract long count();

  @Override
  public abstract ImmutableRangeMap<Double, Long> intervalCounts();

  @Override
  public abstract DistributionFitter distributionFitter();
}
