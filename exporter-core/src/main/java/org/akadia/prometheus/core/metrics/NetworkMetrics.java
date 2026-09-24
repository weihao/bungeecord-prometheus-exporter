package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.platform.NetworkStatsProvider;

/**
 * Network-wide counts reported by a multi-proxy coordinator (RedisBungee).
 *
 * <p>Registered only when {@code metrics.redisbungee} is on. Off by default: with several
 * proxies scraped individually, these series are identical on every one of them, and
 * summing them in PromQL multiplies the network player count by the number of proxies.
 *
 * <p>The callbacks emit nothing while the coordinator is unavailable, so a RedisBungee
 * that enables after this plugin starts reporting on its own rather than being stuck at a
 * misleading zero for the rest of the run.
 */
public final class NetworkMetrics {

    public NetworkMetrics(ExporterConfig config,
                          NetworkStatsProvider provider,
                          MetricRegistrar registrar) {
        registrar.register(MetricNames.REDIS_PLAYERS_ONLINE, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.REDIS_PLAYERS_ONLINE))
                .help("Players online across the whole network, as reported by RedisBungee. "
                        + "Identical on every proxy; do not sum across instances")
                .callback(callback -> {
                    if (provider.isAvailable()) {
                        callback.call(provider.networkPlayerCount());
                    }
                })
                .build());

        registrar.register(MetricNames.REDIS_PROXIES_ONLINE, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.REDIS_PROXIES_ONLINE))
                .help("Proxies participating in the RedisBungee network")
                .callback(callback -> {
                    if (provider.isAvailable()) {
                        callback.call(provider.proxyCount());
                    }
                })
                .build());
    }
}
