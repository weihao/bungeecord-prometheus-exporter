package org.akadia.prometheus.listeners;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.event.ClientConnectEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.utils.Util;

public class LoginEventListener implements Listener {

    static final Counter requests = Counter.build()
            .name(Util.prefix("login_total"))
            .help("total logins")
            .register();

    @EventHandler
    public void onLoginEvent(LoginEvent event) {
        requests.inc();
    }
}
