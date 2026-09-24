package org.akadia.prometheus.core;

import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.http.MetricsHttpServer;
import org.akadia.prometheus.core.metrics.BackendHealthMetrics;
import org.akadia.prometheus.core.metrics.CardinalityGuard;
import org.akadia.prometheus.core.metrics.EventMetrics;
import org.akadia.prometheus.core.metrics.ExporterSelfMetrics;
import org.akadia.prometheus.core.metrics.MetricRegistrar;
import org.akadia.prometheus.core.metrics.NetworkMetrics;
import org.akadia.prometheus.core.metrics.PlayerMetrics;
import org.akadia.prometheus.core.metrics.PlayerPingMetrics;
import org.akadia.prometheus.core.metrics.PluginMetrics;
import org.akadia.prometheus.core.metrics.ProxyInfoMetrics;
import org.akadia.prometheus.core.metrics.SamplerService;
import org.akadia.prometheus.core.metrics.ServerMetrics;
import org.akadia.prometheus.core.platform.NetworkStatsProvider;
import org.akadia.prometheus.core.platform.PlatformLogger;
import org.akadia.prometheus.core.platform.ProxyPlatform;

import java.io.IOException;
import java.time.Clock;

/**
 * Assembles and owns the whole exporter: registry, collectors, samplers and HTTP endpoint.
 *
 * <p>One instance per plugin enable. {@link #close()} reverses everything, which is what
 * makes {@code /reload} and repeated enable/disable cycles safe - the previous version
 * never stopped its HTTP server, so a reload left the port bound and the exporter dead
 * until the proxy restarted.
 *
 * <p>Each instance gets its own {@link PrometheusRegistry} rather than the JVM-wide
 * default, so a second enable cannot collide with leftovers from the first.
 */
public final class ProxyExporter implements AutoCloseable {

    private final ExporterConfig config;
    private final PlatformLogger logger;
    private final PrometheusRegistry registry;
    private final CardinalityGuard guard;
    private final ExporterSelfMetrics selfMetrics;
    private final EventMetrics eventMetrics;
    private final SamplerService samplers;
    private final MetricsHttpServer httpServer;

    private ProxyExporter(Builder builder) throws IOException {
        this.config = builder.config;
        this.logger = builder.platform.logger();
        this.registry = new PrometheusRegistry();

        this.selfMetrics = new ExporterSelfMetrics(config, registry);
        this.guard = new CardinalityGuard(config.maxSeriesPerMetric(), (metric, dropped, firstTime) -> {
            selfMetrics.recordDroppedSeries(metric, dropped);
            if (firstTime) {
                logger.warn("Metric '" + metric + "' reached its cardinality budget of "
                        + config.maxSeriesPerMetric() + " series; further label combinations "
                        + "are dropped. Raise collection.max-series-per-metric or disable the "
                        + "high-cardinality metric groups.");
            }
        });

        MetricRegistrar registrar = new MetricRegistrar(registry, selfMetrics, logger);
        ProxyPlatform platform = builder.platform;

        if (config.isEnabled(MetricId.JVM)) {
            // Deliberately NOT namespaced. jvm_* and process_* are cross-language standard
            // names; prefixing them (as the previous version did) breaks every off-the-shelf
            // JVM dashboard and alert rule.
            JvmMetrics.builder().register(registry);
        }

        if (config.isEnabled(MetricId.PROXY_INFO)) {
            new ProxyInfoMetrics(config, platform.identity(), registrar, builder.clock);
        }

        new PlayerMetrics(config, platform, guard, registrar);
        new ServerMetrics(config, platform, guard, registrar);
        new PluginMetrics(config, platform, guard, registrar);

        this.eventMetrics = new EventMetrics(config, guard, registrar);

        PlayerPingMetrics playerPing = new PlayerPingMetrics(config, platform, registrar);
        BackendHealthMetrics backendHealth =
                new BackendHealthMetrics(config, platform, guard, registrar);

        if (config.isEnabled(MetricId.REDISBUNGEE)) {
            if (builder.networkStats == null) {
                logger.warn("metrics.redisbungee is enabled but this platform supplied no "
                        + "network coordinator; the redis_* metrics will not be exported");
            } else {
                new NetworkMetrics(config, builder.networkStats, registrar);
                if (!builder.networkStats.isAvailable()) {
                    // Not fatal: the binding retries, so a RedisBungee that enables after
                    // this plugin starts reporting on its own.
                    logger.warn("metrics.redisbungee is enabled but RedisBungee is not "
                            + "available yet; the redis_* metrics stay absent until it is");
                }
            }
        }

        this.samplers = new SamplerService(selfMetrics, logger);
        if (config.isEnabled(MetricId.BACKEND_HEALTH)) {
            samplers.schedule(BackendHealthMetrics.SAMPLER_NAME,
                    config.backendPingInterval(), backendHealth::refresh);
        }
        if (playerPing.isEnabled()) {
            samplers.schedule(PlayerPingMetrics.SAMPLER_NAME,
                    config.playerPingSampleInterval(), playerPing::sample);
        }

        this.httpServer = new MetricsHttpServer(config, registry, logger);
        try {
            this.httpServer.start();
        } catch (IOException | RuntimeException e) {
            // The samplers are already scheduled by this point. Without this, a failure to
            // bind the port - the common case being a reload that left the old port taken -
            // would leave daemon threads pinging every backend forever with no endpoint to
            // serve the results.
            samplers.close();
            registry.clear();
            throw e;
        }

        logger.info("Prometheus exporter " + ExporterBuild.version() + " ready on "
                + platform.identity() + "; " + config.enabledMetrics().size()
                + " metric groups enabled");
    }

    public static Builder builder(ExporterConfig config, ProxyPlatform platform) {
        return new Builder(config, platform);
    }

    /** Event counters the platform adapters feed from their listeners. */
    public EventMetrics events() {
        return eventMetrics;
    }

    /** The registry backing {@code /metrics}. Exposed for tests and diagnostics. */
    public PrometheusRegistry registry() {
        return registry;
    }

    /**
     * Runs every background sampler once, synchronously.
     *
     * <p>The samplers already run on their own schedule; this exists so tests can assert on
     * sampled metrics immediately instead of sleeping through an interval.
     */
    public void runSamplersNow() {
        samplers.runAllNow();
    }

    /** Port the endpoint is bound to, resolved if the configured port was 0. */
    public int port() {
        return httpServer.port();
    }

    public ExporterConfig config() {
        return config;
    }

    @Override
    public void close() {
        // Stop accepting scrapes first, then the samplers, so nothing observes a
        // half-torn-down state.
        httpServer.close();
        samplers.close();
        registry.clear();
        guard.reset();
    }

    /** Fluent construction; building starts the exporter. */
    public static final class Builder {
        private final ExporterConfig config;
        private final ProxyPlatform platform;
        private NetworkStatsProvider networkStats;
        private Clock clock = Clock.systemUTC();

        private Builder(ExporterConfig config, ProxyPlatform platform) {
            this.config = config;
            this.platform = platform;
        }

        /** Optional multi-proxy coordinator; null when none is installed. */
        public Builder networkStats(NetworkStatsProvider networkStats) {
            this.networkStats = networkStats;
            return this;
        }

        /** Overridable for deterministic uptime assertions in tests. */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Registers every enabled collector, starts the samplers and binds the endpoint.
         *
         * @throws IOException if the metrics port cannot be bound
         */
        public ProxyExporter start() throws IOException {
            return new ProxyExporter(this);
        }
    }
}
