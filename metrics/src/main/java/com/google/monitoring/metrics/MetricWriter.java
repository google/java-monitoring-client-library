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

import java.io.Flushable;
import java.io.IOException;

/** An interface for exporting Metrics. */
public interface MetricWriter extends Flushable {

  /**
   * Writes a {@link MetricPoint} to the writer's destination.
   *
   * <p>The write may be asynchronous.
   *
   * @throws IOException if the provided metric cannot be represented by the writer or if the metric
   *     cannot be flushed.
   */
  <V> void write(MetricPoint<V> metricPoint) throws IOException;

  /** Forces the writer to synchronously write all buffered metric values. */
  @Override
  void flush() throws IOException;
}
