package org.akadia.prometheus.bungeecord.metrics;

import org.akadia.prometheus.bungeecord.PrometheusBungeeCordExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class OnlinePlayers extends GauageMetric {

    public OnlinePlayers(PrometheusBungeeCordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        ((PrometheusBungeeCordExporter) getPlugin())
                .getProxy()
                .getServers()
                .forEach((key, value) -> this.getGauge().labels(key).set(value.getPlayers().size()));
    }

    @Override
    public String getConfigKey() {
        return "online_players";
    }

    @Override
    public String getHelp() {
        return "the number of online players in BungeeCord";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
