package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.core.metrics.Info;
import io.prometheus.metrics.model.snapshots.Unit;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.platform.ProxyIdentity;

import java.time.Clock;

/**
 * Identity and uptime of the proxy.
 *
 * <p>{@code _info} carries the version strings as labels with a constant value of 1, the
 * standard Prometheus way to attach metadata without creating a metric per attribute.
 * Join it onto other series in PromQL with {@code * on(instance) group_left(version)}.
 */
public final class ProxyInfoMetrics {

    private final Clock clock;
    private final long startedAtMillis;

    public ProxyInfoMetrics(ExporterConfig config,
                            ProxyIdentity identity,
                            MetricRegistrar registrar,
                            Clock clock) {
        this.clock = clock;
        this.startedAtMillis = clock.millis();

        registrar.register(MetricNames.INFO, Info.builder()
                        .name(config.metricName(MetricNames.INFO))
                        .help("Proxy identity. Always 1; the information is in the labels")
                        .labelNames(MetricNames.LABEL_TYPE, MetricNames.LABEL_NAME,
                                MetricNames.LABEL_VERSION, MetricNames.LABEL_ONLINE_MODE)
                        .build())
                .setLabelValues(identity.type(), identity.name(), identity.version(),
                        Boolean.toString(identity.onlineMode()));

        Gauge startTime = registrar.register(MetricNames.START_TIME_SECONDS, Gauge.builder()
                .name(config.metricName(MetricNames.START_TIME_SECONDS))
                .unit(Unit.SECONDS)
                .help("Unix timestamp at which the exporter started")
                .build());
        startTime.set(startedAtMillis / 1000.0d);

        registrar.register(MetricNames.UPTIME_SECONDS, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.UPTIME_SECONDS))
                .unit(Unit.SECONDS)
                .help("Seconds since the exporter started")
                .callback(callback -> callback.call(uptimeSeconds()))
                .build());
    }

    double uptimeSeconds() {
        return (clock.millis() - startedAtMillis) / 1000.0d;
    }
}
