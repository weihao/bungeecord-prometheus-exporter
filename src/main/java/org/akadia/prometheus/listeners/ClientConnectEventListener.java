package org.akadia.prometheus.listeners;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.event.ClientConnectEvent;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.utils.Util;

public class ClientConnectEventListener implements Listener {

    static final Counter requests = Counter.build()
            .name(Util.prefix("client_connect_total"))
            .help("total client connect")
            .register();

    @EventHandler
    public void onClientConnectEvent(ClientConnectEvent event) {
        requests.inc();
    }
}
