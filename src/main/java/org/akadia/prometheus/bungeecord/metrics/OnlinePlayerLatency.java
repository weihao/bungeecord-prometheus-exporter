package org.akadia.prometheus.bungeecord.metrics;

import org.akadia.prometheus.bungeecord.PrometheusBungeeCordExporter;
import org.akadia.prometheus.interfaces.SummaryMetric;

public class OnlinePlayerLatency extends SummaryMetric {

    public OnlinePlayerLatency(PrometheusBungeeCordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        ((PrometheusBungeeCordExporter) getPlugin())
                .getProxy()
                .getPlayers()
                .forEach(proxiedPlayer ->
                        this.getSummary().labels(proxiedPlayer.getName()).observe(proxiedPlayer.getPing())
                );
    }

    @Override
    public String getConfigKey() {
        return "online_player_latency";
    }

    @Override
    public String getHelp() {
        return "the latency of an online player in BungeeCord";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"name"};
    }
}
