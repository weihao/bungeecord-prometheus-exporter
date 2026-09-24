package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.core.metrics.Info;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Unit;
import org.akadia.prometheus.core.ExporterBuild;
import org.akadia.prometheus.core.config.ExporterConfig;

/**
 * Metrics about the exporter itself.
 *
 * <p>An exporter that silently drops data is worse than one that is plainly down, so the
 * failure modes that are otherwise invisible - a collector throwing, the cardinality
 * guard clipping series, a background sampler stalling - are all counted here and
 * alertable.
 */
public final class ExporterSelfMetrics {

    private final Counter scrapeErrors;
    private final Counter seriesDropped;
    private final Counter samplerRuns;
    private final Histogram samplerDuration;

    public ExporterSelfMetrics(ExporterConfig config, PrometheusRegistry registry) {
        Info.builder()
                .name(config.metricName(MetricNames.EXPORTER_BUILD_INFO))
                .help("Build information for the proxy Prometheus exporter")
                .labelNames(MetricNames.LABEL_VERSION, "java_target", "prometheus_client",
                        "velocity_api", "bungeecord_api")
                .register(registry)
                .setLabelValues(ExporterBuild.version(), ExporterBuild.javaTarget(),
                        ExporterBuild.prometheusClient(), ExporterBuild.velocityApi(),
                        ExporterBuild.bungeecordApi());

        this.scrapeErrors = Counter.builder()
                .name(config.metricName(MetricNames.EXPORTER_SCRAPE_ERRORS_TOTAL))
                .help("Collectors that threw while answering a scrape. A non-zero rate means "
                        + "metrics are missing from the response")
                .labelNames(MetricNames.LABEL_COLLECTOR)
                .register(registry);

        this.seriesDropped = Counter.builder()
                .name(config.metricName(MetricNames.EXPORTER_SERIES_DROPPED_TOTAL))
                .help("Time series suppressed because the metric hit its cardinality budget")
                .labelNames(MetricNames.LABEL_METRIC)
                .register(registry);

        this.samplerRuns = Counter.builder()
                .name(config.metricName(MetricNames.EXPORTER_SAMPLER_RUNS_TOTAL))
                .help("Background sampler executions by outcome")
                .labelNames(MetricNames.LABEL_SAMPLER, MetricNames.LABEL_RESULT)
                .register(registry);

        this.samplerDuration = Histogram.builder()
                .name(config.metricName(MetricNames.EXPORTER_SAMPLER_DURATION_SECONDS))
                .unit(Unit.SECONDS)
                .help("Wall time taken by each background sampler run")
                .labelNames(MetricNames.LABEL_SAMPLER)
                .classicUpperBounds(0.005, 0.01, 0.05, 0.1, 0.5, 1, 2.5, 5, 10)
                .register(registry);
    }

    public void recordScrapeError(String collector) {
        scrapeErrors.labelValues(collector).inc();
    }

    public void recordDroppedSeries(String metric, long count) {
        seriesDropped.labelValues(metric).inc(count);
    }

    public void recordSamplerRun(String sampler, boolean success, double durationSeconds) {
        samplerRuns.labelValues(sampler, success ? "success" : "failure").inc();
        samplerDuration.labelValues(sampler).observe(durationSeconds);
    }
}
