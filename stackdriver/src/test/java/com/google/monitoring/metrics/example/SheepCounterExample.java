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

package com.google.monitoring.metrics.example;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.MonitoringScopes;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.monitoring.metrics.EventMetric;
import com.google.monitoring.metrics.IncrementableMetric;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.LinearFitter;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricRegistryImpl;
import com.google.monitoring.metrics.MetricReporter;
import com.google.monitoring.metrics.MetricWriter;
import com.google.monitoring.metrics.SettableMetric;
import com.google.monitoring.metrics.stackdriver.StackdriverWriter;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/** A sample application which uses the Metrics API to count sheep while sleeping. */
public final class SheepCounterExample {

  /*
   * The code below for using a custom {@link LogManager} is only necessary to enable logging at JVM
   * shutdown to show the shutdown logs of {@link MetricReporter} in this small standalone
   * application.
   *
   * <p>It is NOT necessary for normal use of the Metrics library.
   */
  static {
    // must be called before any Logger method is used.
    System.setProperty("java.util.logging.manager", DelayedShutdownLogManager.class.getName());
  }

  private static final Logger logger = Logger.getLogger(SheepCounterExample.class.getName());

  /**
   * The time interval, in seconds, between when the {@link MetricReporter} will read {@link Metric}
   * instances and enqueue them to the {@link MetricWriter}.
   *
   * @see MetricReporter
   */
  private static final long METRICS_REPORTING_INTERVAL = 30L;

  /**
   * The maximum queries per second to the Stackdriver API. Contact Cloud Support to raise this from
   * the default value if necessary.
   */
  private static final int STACKDRIVER_MAX_QPS = 30;

  /**
   * The maximum number of {@link com.google.api.services.monitoring.v3.model.TimeSeries} that can
   * be bundled into a single {@link
   * com.google.api.services.monitoring.v3.model.CreateTimeSeriesRequest}. This must be at most 200.
   * Setting this lower will cause the {@link StackdriverWriter} to {@link
   * StackdriverWriter#flush()} more frequently.
   */
  private static final int STACKDRIVER_MAX_POINTS_PER_REQUEST = 200;

  // Create some metrics to track your ZZZs.
  private static final ImmutableList<String> SHEEP_COLORS =
      ImmutableList.of("Green", "Yellow", "Red", "Blue");
  private static final ImmutableList<String> SHEEP_SPECIES =
      ImmutableList.of("Domestic", "Bighorn");
  private static final ImmutableSet<LabelDescriptor> SHEEP_ATTRIBUTES =
      ImmutableSet.of(
          LabelDescriptor.create("color", "Sheep Color"),
          LabelDescriptor.create("species", "Sheep Species"));

  /**
   * Counters are good for tracking monotonically increasing values, like request counts or error
   * counts. Or, in this case, sheep.
   */
  private static final IncrementableMetric sheepCounter =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/sheep", "Counts sheep over time.", "Number of Sheep", SHEEP_ATTRIBUTES);

  /**
   * Settable metrics are good for state indicators. For example, you could use one to track the
   * lifecycle of a {@link com.google.common.util.concurrent.Service}. In this case, we are just
   * using it to track the sleep state of this application.
   */
  private static final SettableMetric<Boolean> isSleeping =
      MetricRegistryImpl.getDefault()
          .newSettableMetric(
              "/is_sleeping",
              "Tracks sleep state.",
              "Sleeping?",
              ImmutableSet.<LabelDescriptor>of(),
              Boolean.class);

  /**
   * Gauge metrics never need to be accessed, so the assignment here is unnecessary. You only need
   * it if you plan on calling {@link Metric#getTimestampedValues()} to read the values of the
   * metric in the code yourself.
   */
  @SuppressWarnings("unused")
  private static final Metric<Double> sleepQuality =
      MetricRegistryImpl.getDefault()
          .newGauge(
              "/sleep_quality",
              "Quality of the sleep.",
              "Quality",
              ImmutableSet.<LabelDescriptor>of(),
              () -> ImmutableMap.of(ImmutableList.<String>of(), new Random().nextDouble()),
              Double.class);

  /**
   * Event metrics track aspects of an "event." Here, we track the fluffiness of the sheep we've
   * seen.
   */
  private static final EventMetric sheepFluffiness =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/sheep_fluffiness",
              "Measures the fluffiness of seen sheep.",
              "Fill Power",
              SHEEP_ATTRIBUTES,
              LinearFitter.create(5, 20.0, 20.0));

  private static Monitoring createAuthorizedMonitoringClient() throws IOException {
    // Grab the Application Default Credentials from the environment.
    // Generate these with 'gcloud beta auth application-default login'
    GoogleCredential credential =
        GoogleCredential.getApplicationDefault().createScoped(MonitoringScopes.all());

    // Create and return the CloudMonitoring service object
    HttpTransport httpTransport = new NetHttpTransport();
    JsonFactory jsonFactory = new JacksonFactory();
    return new Monitoring.Builder(httpTransport, jsonFactory, credential)
        .setApplicationName("Monitoring Sample")
        .build();
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Missing required project argument");
      System.err.printf(
          "Usage: java %s gcp-project-id [verbose]\n", SheepCounterExample.class.getName());
      return;
    }
    String project = args[0];

    // Turn up the logging verbosity
    if (args.length > 1) {
      Logger log = LogManager.getLogManager().getLogger("");
      log.setLevel(Level.ALL);
      for (Handler h : log.getHandlers()) {
        h.setLevel(Level.ALL);
      }
    }

    // Create a sample resource. In this case, a GCE Instance.
    // See https://cloud.google.com/monitoring/api/resources for a list of resource types.
    MonitoredResource monitoredResource =
        new MonitoredResource()
            .setType("gce_instance")
            .setLabels(
                ImmutableMap.of(
                    "instance_id", "test-instance",
                    "zone", "us-central1-f"));

    // Set up the Metrics infrastructure.
    MetricWriter stackdriverWriter =
        new StackdriverWriter(
            createAuthorizedMonitoringClient(),
            project,
            monitoredResource,
            STACKDRIVER_MAX_QPS,
            STACKDRIVER_MAX_POINTS_PER_REQUEST);
    final MetricReporter reporter =
        new MetricReporter(
            stackdriverWriter, METRICS_REPORTING_INTERVAL, Executors.defaultThreadFactory());
    reporter.startAsync().awaitRunning();

    // Set up a handler to stop sleeping on SIGINT.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  reporter.stopAsync().awaitTerminated();
                  // Allow the LogManager to cleanup the loggers.
                  DelayedShutdownLogManager.resetFinally();
                }));

    System.err.println("Send SIGINT (Ctrl+C) to stop sleeping.");
    while (true) {
      // Count some Googley sheep.
      int colorIndex = new Random().nextInt(SHEEP_COLORS.size());
      int speciesIndex = new Random().nextInt(SHEEP_SPECIES.size());
      sheepCounter.incrementBy(1, SHEEP_COLORS.get(colorIndex), SHEEP_SPECIES.get(speciesIndex));
      sheepFluffiness.record(
          new Random().nextDouble() * 200,
          SHEEP_COLORS.get(colorIndex),
          SHEEP_SPECIES.get(speciesIndex));
      isSleeping.set(true);

      logger.info("zzz...");
      Thread.sleep(5000);
    }
  }

  /**
   * Special {@link LogManager} with a no-op {@link LogManager#reset()} so that logging can proceed
   * as usual until stopped in in another runtime shutdown hook.
   *
   * <p>The class is marked public because it is loaded by the JVM classloader at runtime.
   */
  @SuppressWarnings("WeakerAccess")
  public static class DelayedShutdownLogManager extends LogManager {

    private static DelayedShutdownLogManager instance;

    public DelayedShutdownLogManager() {
      instance = this;
    }

    /** A no-op implementation. */
    @Override
    public void reset() {
      /* don't reset yet. */
    }

    static void resetFinally() {
      instance.delayedReset();
    }

    private void delayedReset() {
      super.reset();
    }
  }
}
