package org.akadia.prometheus.metrics;

import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.GauageMetric;

public class OnlineServers extends GauageMetric {

    public OnlineServers(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().set(getPlugin().getProxy().getServers().size());
    }

    @Override
    public String getConfigKey() {
        return "online_servers";
    }

    @Override
    public String getHelp() {
        return "online servers";
    }
}
