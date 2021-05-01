package org.akadia.prometheus.listeners;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.utils.Util;

public class ProxyPingEventListener implements Listener {

    static final Counter requests = Counter.build()
            .name(Util.prefix("ping_total"))
            .help("total pings")
            .register();

    @EventHandler
    public void onProxyPingEvent(ProxyPingEvent event) {
        requests.inc();
    }
}
