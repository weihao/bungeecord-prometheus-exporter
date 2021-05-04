package org.akadia.prometheus.listeners;

import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class ProxyPingEventListener extends CountableMetrics {

    public ProxyPingEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onProxyPingEvent(ProxyPingEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "total pings";
    }

    @Override
    public String getConfigKey() {
        return "ping_total";
    }
}
