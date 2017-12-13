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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.monitoring.metrics.MetricsUtils.checkDouble;

import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;
import com.google.common.primitives.Doubles;
import java.util.Map;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable {@link Distribution}. Instances of this class <b>should not</b> be used to construct
 * {@link MetricPoint} instances as {@link MetricPoint} instances are supposed to represent
 * immutable values.
 *
 * @see ImmutableDistribution
 */
@NotThreadSafe
public final class MutableDistribution implements Distribution {

  private final TreeRangeMap<Double, Long> intervalCounts;
  private final DistributionFitter distributionFitter;
  private double sumOfSquaredDeviation = 0.0;
  private double mean = 0.0;
  private long count = 0;

  /** Constructs an empty Distribution with the specified {@link DistributionFitter}. */
  public MutableDistribution(DistributionFitter distributionFitter) {
    this.distributionFitter = checkNotNull(distributionFitter);
    ImmutableSortedSet<Double> boundaries = distributionFitter.boundaries();

    checkArgument(boundaries.size() > 0);
    checkArgument(Ordering.natural().isOrdered(boundaries));

    this.intervalCounts = TreeRangeMap.create();

    double[] boundariesArray = Doubles.toArray(distributionFitter.boundaries());

    // Add underflow and overflow intervals
    this.intervalCounts.put(Range.lessThan(boundariesArray[0]), 0L);
    this.intervalCounts.put(Range.atLeast(boundariesArray[boundariesArray.length - 1]), 0L);

    // Add finite intervals
    for (int i = 1; i < boundariesArray.length; i++) {
      this.intervalCounts.put(Range.closedOpen(boundariesArray[i - 1], boundariesArray[i]), 0L);
    }
  }

  public void add(double value) {
    add(value, 1L);
  }

  public void add(double value, long numSamples) {
    checkArgument(numSamples >= 0, "numSamples must be non-negative");
    checkDouble(value);

    // having numSamples = 0 works as expected (does nothing) even if we let it continue, but we
    // can short-circuit it by returning early.
    if (numSamples == 0) {
      return;
    }

    Map.Entry<Range<Double>, Long> entry = intervalCounts.getEntry(value);
    intervalCounts.put(entry.getKey(), entry.getValue() + numSamples);
    this.count += numSamples;

    // Update mean and sumOfSquaredDeviation using Welford's method
    // See Knuth, "The Art of Computer Programming", Vol. 2, page 232, 3rd edition
    double delta = value - mean;
    mean += delta * numSamples / count;
    sumOfSquaredDeviation += delta * (value - mean) * numSamples;
  }

  @Override
  public double mean() {
    return mean;
  }

  @Override
  public double sumOfSquaredDeviation() {
    return sumOfSquaredDeviation;
  }

  @Override
  public long count() {
    return count;
  }

  @Override
  public ImmutableRangeMap<Double, Long> intervalCounts() {
    return ImmutableRangeMap.copyOf(intervalCounts);
  }

  @Override
  public DistributionFitter distributionFitter() {
    return distributionFitter;
  }
}
