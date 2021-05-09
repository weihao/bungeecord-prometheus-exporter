package org.akadia.prometheus.metrics;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.GauageMetric;

import java.util.Map;

public class PlayersOnlineTotal extends GauageMetric {

    public PlayersOnlineTotal(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        Map<String, ServerInfo> servers = getPlugin().getProxy().getServers();
        for (String key : servers.keySet()) {
            this.getGauge().labels(key).set(servers.get(key).getPlayers().size());
        }
    }

    @Override
    public String getConfigKey() {
        return "players_online_total";
    }

    @Override
    public String getHelp() {
        return "unique online players";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
