package org.akadia.prometheus.listeners;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.utils.Util;

public class PlayerDisconnectEventListener implements Listener {

    static final Counter requests = Counter.build()
            .name(Util.prefix("player_disconnects_total"))
            .help("total player disconnects")
            .register();

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent event) {
        requests.inc();
    }
}
