package org.akadia.prometheus.listeners;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.utils.Util;

public class PreLoginEventListener implements Listener {

    static final Counter requests = Counter.build()
            .name(Util.prefix("pre_login_total"))
            .help("total pre logins")
            .register();

    @EventHandler
    public void onPreLoginEvent(PreLoginEvent event) {
        requests.inc();
    }
}
