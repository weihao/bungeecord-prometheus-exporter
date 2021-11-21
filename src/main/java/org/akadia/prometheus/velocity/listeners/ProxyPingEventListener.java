package org.akadia.prometheus.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class ProxyPingEventListener extends CountableMetrics {

    public ProxyPingEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @Subscribe
    public void onProxyPingEvent(ProxyPingEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of server list pings in Velocity";
    }

    @Override
    public String getConfigKey() {
        return "server_list_pings";
    }
}
