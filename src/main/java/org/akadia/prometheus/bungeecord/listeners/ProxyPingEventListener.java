package org.akadia.prometheus.bungeecord.listeners;

import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class ProxyPingEventListener extends CountableMetrics implements Listener {

    public ProxyPingEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onProxyPingEvent(ProxyPingEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of server list pings in Bungeecord";
    }

    @Override
    public String getConfigKey() {
        return "server_list_pings";
    }
}
