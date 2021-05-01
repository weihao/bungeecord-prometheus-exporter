package org.akadia.prometheus.metrics;

import io.prometheus.client.Gauge;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.utils.Util;

import java.util.Map;

public class PlayersOnlineTotal extends Metric {

    private static final Gauge PLAYERS_ONLINE = Gauge.build()
            .name(Util.prefix("players_online_total"))
            .labelNames("server")
            .help("Unique online players")
            .create();

    public PlayersOnlineTotal(Plugin plugin) {
        super(plugin, PLAYERS_ONLINE);
    }

    @Override
    public void doCollect() {
        Map<String, ServerInfo> servers = getPlugin().getProxy().getServers();
        for (String key : servers.keySet()) {
            PLAYERS_ONLINE.labels(key).set(servers.get(key).getPlayers().size());
        }
    }
}
