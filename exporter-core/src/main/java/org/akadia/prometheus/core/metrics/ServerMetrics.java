package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.BackendServerHandle;
import org.akadia.prometheus.core.platform.ProxyPlatform;

/**
 * The backend servers the proxy knows about.
 *
 * <p>{@code server_info} is a gauge rather than an OpenMetrics {@code info} type because
 * the set of servers changes at runtime - Velocity registers and unregisters backends via
 * its API - and a callback gauge re-reads the list on every scrape. This is the same shape
 * node_exporter uses for {@code node_uname_info}.
 */
public final class ServerMetrics {

    public ServerMetrics(ExporterConfig config,
                         ProxyPlatform platform,
                         CardinalityGuard guard,
                         MetricRegistrar registrar) {
        if (!config.isEnabled(MetricId.SERVERS)) {
            return;
        }

        registrar.register(MetricNames.SERVERS_REGISTERED, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.SERVERS_REGISTERED))
                .help("Backend servers registered with the proxy")
                .callback(callback -> callback.call(platform.servers().size()))
                .build());

        String infoMetric = config.metricName(MetricNames.SERVER_INFO);
        registrar.register(MetricNames.SERVER_INFO, GaugeWithCallback.builder()
                .name(infoMetric)
                .help("Always 1 for each registered backend server. The information is in the labels")
                .labelNames(MetricNames.LABEL_SERVER, MetricNames.LABEL_ADDRESS)
                .callback(callback -> {
                    for (BackendServerHandle server : platform.servers()) {
                        if (guard.admit(infoMetric, server.name(), server.address())) {
                            callback.call(1, server.name(), server.address());
                        }
                    }
                })
                .build());
    }
}
