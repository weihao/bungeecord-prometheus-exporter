package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.akadia.prometheus.core.platform.PlatformLogger;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a collector so a failure in one metric cannot fail the whole scrape.
 *
 * <p>Proxy APIs throw: a server gets unregistered mid-iteration, a player disconnects
 * between the count and the read, a third-party plugin's {@code getDescription()} returns
 * null. Without this, a single such call turns {@code /metrics} into an HTTP 500 and
 * Prometheus records the entire target as down - every other metric disappears too.
 *
 * <p>Instead the offending collector contributes nothing to that scrape, the rest of the
 * response is served, and {@code <namespace>_exporter_scrape_errors_total} goes up so the
 * gap is visible rather than silent.
 */
final class GuardedCollector implements MultiCollector {

    private final String name;
    private final MultiCollector delegate;
    private final ExporterSelfMetrics selfMetrics;
    private final PlatformLogger logger;
    private final AtomicBoolean loggedOnce = new AtomicBoolean();

    GuardedCollector(String name,
                     MultiCollector delegate,
                     ExporterSelfMetrics selfMetrics,
                     PlatformLogger logger) {
        this.name = name;
        this.delegate = delegate;
        this.selfMetrics = selfMetrics;
        this.logger = logger;
    }

    @Override
    public MetricSnapshots collect() {
        try {
            MetricSnapshots snapshots = delegate.collect();
            return snapshots == null ? MetricSnapshots.builder().build() : snapshots;
        } catch (RuntimeException | Error e) {
            selfMetrics.recordScrapeError(name);
            // One stack trace is diagnostic; one per scrape is a log flood.
            if (loggedOnce.compareAndSet(false, true)) {
                logger.error("Collector '" + name + "' failed and was skipped for this scrape. "
                        + "Further failures are counted in "
                        + MetricNames.EXPORTER_SCRAPE_ERRORS_TOTAL + " but not logged.", e);
            }
            return MetricSnapshots.builder().build();
        }
    }

    @Override
    public List<String> getPrometheusNames() {
        // The registry uses this for duplicate-name detection at registration time, so it
        // has to reflect the delegate rather than the empty fallback.
        return delegate.getPrometheusNames();
    }
}
