package org.akadia.prometheus.metrics;

import io.prometheus.client.Gauge;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.utils.Util;

import java.util.Map;

public class ServersOnlineTotal extends Metric {

    private static final Gauge SERVERS_ONLINE = Gauge.build()
            .name(Util.prefix("servers_online_total"))
            .help("Unique online servers")
            .create();

    public ServersOnlineTotal(Plugin plugin) {
        super(plugin, SERVERS_ONLINE);
    }

    @Override
    public void doCollect() {
        SERVERS_ONLINE.set(getPlugin().getProxy().getServers().size());
    }
}
