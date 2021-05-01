package org.akadia.prometheus.listeners;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.utils.Util;

public class PostLoginEventListener implements Listener {

    static final Counter requests = Counter.build()
            .name(Util.prefix("post_login_total"))
            .help("total post logins")
            .register();

    @EventHandler
    public void onPostLoginEvent(PostLoginEvent event) {
        requests.inc();
    }
}
