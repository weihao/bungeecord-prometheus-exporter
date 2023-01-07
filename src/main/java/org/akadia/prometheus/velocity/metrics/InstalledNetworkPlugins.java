package org.akadia.prometheus.velocity.metrics;

import org.akadia.prometheus.interfaces.GauageMetric;
import org.akadia.prometheus.velocity.PrometheusVelocityExporter;

public class InstalledNetworkPlugins extends GauageMetric {

    public InstalledNetworkPlugins(PrometheusVelocityExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().set(((PrometheusVelocityExporter) getPlugin()).getProxyServer().getPluginManager().getPlugins().size());
    }

    @Override
    public String getConfigKey() {
        return "installed_network_plugins";
    }

    @Override
    public String getHelp() {
        return "the number of installed network plugins in Velocity";
    }
}
