package org.akadia.prometheus.velocity.metrics;

import org.akadia.prometheus.interfaces.SummaryMetric;
import org.akadia.prometheus.velocity.PrometheusVelocityExporter;

public class OnlinePlayersLatency extends SummaryMetric {

    public OnlinePlayersLatency(PrometheusVelocityExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        ((PrometheusVelocityExporter) getPlugin()).getProxyServer().getAllPlayers().forEach(player -> this.getSummary().labels(player.getUsername()).observe(player.getPing()));
    }


    @Override
    public String getConfigKey() {
        return "online_player_latency";
    }

    @Override
    public String getHelp() {
        return "the latency of an online player in Velocity";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"name"};
    }
}
