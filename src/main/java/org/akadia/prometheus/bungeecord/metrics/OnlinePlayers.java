package org.akadia.prometheus.bungeecord.metrics;

import net.md_5.bungee.api.config.ServerInfo;
import org.akadia.prometheus.bungeecord.PrometheusBungeecordExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

import java.util.Map;

public class OnlinePlayers extends GauageMetric {

    public OnlinePlayers(PrometheusBungeecordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        Map<String, ServerInfo> servers = ((PrometheusBungeecordExporter) getPlugin()).getProxy().getServers();
        for (String key : servers.keySet()) {
            this.getGauge().labels(key).set(servers.get(key).getPlayers().size());
        }
    }

    @Override
    public String getConfigKey() {
        return "online_players";
    }

    @Override
    public String getHelp() {
        return "the number of online players in Bungeecord";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
