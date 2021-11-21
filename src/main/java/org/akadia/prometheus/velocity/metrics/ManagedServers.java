package org.akadia.prometheus.velocity.metrics;

import org.akadia.prometheus.interfaces.GauageMetric;
import org.akadia.prometheus.velocity.PrometheusVelocityExporter;

public class ManagedServers extends GauageMetric {

    public ManagedServers(PrometheusVelocityExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().set(((PrometheusVelocityExporter) getPlugin()).getProxyServer().getAllServers().size());
    }

    @Override
    public String getConfigKey() {
        return "managed_servers";
    }

    @Override
    public String getHelp() {
        return "the number of managed servers in Velocity";
    }
}
