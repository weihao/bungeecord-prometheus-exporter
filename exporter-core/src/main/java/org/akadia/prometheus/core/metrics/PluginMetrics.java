package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.PluginDescriptor;
import org.akadia.prometheus.core.platform.ProxyPlatform;

/**
 * Plugins loaded on the proxy.
 *
 * <p>Useful for alerting on drift across a fleet of proxies: a single
 * {@code count by (plugin, version)} query tells you which node is running the odd build
 * after a partial rollout.
 */
public final class PluginMetrics {

    public PluginMetrics(ExporterConfig config,
                         ProxyPlatform platform,
                         CardinalityGuard guard,
                         MetricRegistrar registrar) {
        if (!config.isEnabled(MetricId.PLUGINS)) {
            return;
        }

        registrar.register(MetricNames.PLUGINS_INSTALLED, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.PLUGINS_INSTALLED))
                .help("Plugins loaded by the proxy")
                .callback(callback -> callback.call(platform.plugins().size()))
                .build());

        String infoMetric = config.metricName(MetricNames.PLUGIN_INFO);
        registrar.register(MetricNames.PLUGIN_INFO, GaugeWithCallback.builder()
                .name(infoMetric)
                .help("Always 1 for each loaded plugin. The information is in the labels")
                .labelNames(MetricNames.LABEL_PLUGIN, MetricNames.LABEL_VERSION)
                .callback(callback -> {
                    for (PluginDescriptor plugin : platform.plugins()) {
                        if (guard.admit(infoMetric, plugin.id(), plugin.version())) {
                            callback.call(1, plugin.id(), plugin.version());
                        }
                    }
                })
                .build());
    }
}
