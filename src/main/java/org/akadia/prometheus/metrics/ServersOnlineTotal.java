package org.akadia.prometheus.metrics;

import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.GauageMetric;

public class ServersOnlineTotal extends GauageMetric {

    public ServersOnlineTotal(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().set(getPlugin().getProxy().getServers().size());
    }

    @Override
    public String getConfigKey() {
        return "servers_online_total";
    }

    @Override
    public String getHelp() {
        return "total online servers";
    }
}
