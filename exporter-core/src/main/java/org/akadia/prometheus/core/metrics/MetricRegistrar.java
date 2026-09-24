package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.akadia.prometheus.core.platform.PlatformLogger;

import java.util.List;

/**
 * Registers collectors into a {@link PrometheusRegistry}, wrapping each one in a
 * {@link GuardedCollector} so a single failure degrades one metric instead of the
 * whole endpoint.
 *
 * <p>Metric groups take one of these rather than the registry directly, which is what
 * makes "every collector is guarded" a property of the design instead of a convention
 * each new collector has to remember.
 */
public final class MetricRegistrar {

    private final PrometheusRegistry registry;
    private final ExporterSelfMetrics selfMetrics;
    private final PlatformLogger logger;

    public MetricRegistrar(PrometheusRegistry registry,
                           ExporterSelfMetrics selfMetrics,
                           PlatformLogger logger) {
        this.registry = registry;
        this.selfMetrics = selfMetrics;
        this.logger = logger;
    }

    /**
     * Registers a single-metric collector.
     *
     * @param name      stable identifier used as the {@code collector} label on scrape errors
     * @param collector the collector, typically a built but unregistered metric
     * @return {@code collector}, so callers can keep the reference for updates
     */
    public <T extends Collector> T register(String name, T collector) {
        registry.register(new GuardedCollector(name, asMulti(collector), selfMetrics, logger));
        return collector;
    }

    /** Registers a collector that emits several metric families. */
    public <T extends MultiCollector> T registerMulti(String name, T collector) {
        registry.register(new GuardedCollector(name, collector, selfMetrics, logger));
        return collector;
    }

    /** Registers a pre-built {@link MultiCollector} without wrapping, for library collectors. */
    public void registerRaw(MultiCollector collector) {
        registry.register(collector);
    }

    public PrometheusRegistry registry() {
        return registry;
    }

    private static MultiCollector asMulti(Collector collector) {
        return new MultiCollector() {
            @Override
            public MetricSnapshots collect() {
                return MetricSnapshots.builder().metricSnapshot(collector.collect()).build();
            }

            @Override
            public List<String> getPrometheusNames() {
                return List.of(collector.getPrometheusName());
            }
        };
    }
}
