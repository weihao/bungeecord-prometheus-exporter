package org.akadia.prometheus.velocity.metrics;

import org.akadia.prometheus.interfaces.GauageMetric;
import org.akadia.prometheus.velocity.PrometheusVelocityExporter;

public class OnlinePlayers extends GauageMetric {

    public OnlinePlayers(PrometheusVelocityExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        ((PrometheusVelocityExporter) getPlugin()).getProxyServer().getAllServers().forEach(registeredServer ->
                this.getGauge().labels(registeredServer.getServerInfo().getName()).set(registeredServer.getPlayersConnected().size()));

    }

    @Override
    public String getConfigKey() {
        return "online_players";
    }

    @Override
    public String getHelp() {
        return "the number of online players in Velocity";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
