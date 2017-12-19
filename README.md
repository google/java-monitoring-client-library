# Monitoring Client Library for Java

[![Build
Status](https://travis-ci.org/google/java-monitoring-client-library.svg?branch=master)](https://travis-ci.org/google/java-monitoring-client-library)

This is not an official Google product.

This library provides an API that is powerful and java idiomatic for configuring
and publishing application metrics. A reference implementation using
[Stackdriver Monitoring API v3](https://cloud.google.com/monitoring/api/v3/) is
included, but other monitoring backend implementations can also be used.

Most of the monitoring libraries available are low-level and is tied directly to
the backend. This library provides type safety, retry logic and an
backend-agnostic approach to Java metrics instrumentation.

## Basic concepts

*   Metric Types

    -   `Counters`: monotonically increasing integers. e. g. number of total
        requests.
    -   `EventMetrics`: data points in certain distribution, used in combination
        with a `DistributionFitter`. e. g. latency distribution, request size
        distribution.
    -   `Gauges`: state indicators, used with a callback function to query the
        state. e. g. number of active connections, current memory usage.

    In general, cumulative values are counters and cumulative probability
    distributions, and non-cumulative values are gauges.

    A metric class consists of typed values (for type safety) and string labels.
    The labels are used to identify a specific metric time series, for example,
    a counter for the number of requests can have label "client_ip_address" and
    "protocol". In this example, all requests coming from the same client IP and
    using the same protocol would be counted together.

    Metrics are modeled after [Stackdriver
    Metrics](https://cloud.google.com/monitoring/api/v3/metrics).

## Using the library

*   Registering Metrics

    To register a metric, specify the name is should be registered (in style of
    an URL path), and the set of `LabelDescriptor` of the metric. For example to
    register a counter:

    ```java
    IncrementableMetric myCounter = MetricRegistryImpl.getDefault()
        .newIncrementableMetric(
            "/path/to/record/metrics",
            "description",
            "value_unit",
            labels);
    ```

*   Recording Metrics

    To record a data point to the metric we just registered, call its recording
    method, specify the labels (required), and value (required for `EventMetric`
    ). In case of a `Counter`, just supplying the labels is sufficient as the
    value is implicitly increased by one.

    ```java
      myCounter.increment("label1", "label2", "label3");
    ```

*   Exporting Metrics

    To export metrics to a monitoring backend, you need configure your backend
    accordingly and implement a `MetricWriter` that talks to your backend. A
    `StackdriverWriter` is provided. A `MetricReporter` needs to be constructed
    from the `MetricWriter`:

    ```java
    MetricReporter metricReporter = new MetricReporter(
        metricWriter,
        writeIntervalInSeconds,
        threadFactory);

    ```

    A thread factory is needed so that the metric reporter can run in the
    background periodically to export recorded metrics (in batch) to the
    backend. It is recommended to set the thread to daemon mode so that it does
    not interfere with JVM shutdown. You can use `ThreadFactoryBuilder` from
    [Guava](https://google.github.io/guava/releases/23.0/api/docs/com/google/common/util/concurrent/ThreadFactoryBuilder.html):

    ```java
    ThreadFactory threadFactory = ThreadFactoryBuilder().setDaemon(true).build();
    ```

    Then in you `main` method, start the metric reporter asynchronously:

    ```java
    metricReporter.get().startAsync().awaitRunning(10, TimeUnit.SECONDS);
    ```

    The reporter will now run in the background and automatically exports
    metrics at the given `writeIntervalInSeconds`.
