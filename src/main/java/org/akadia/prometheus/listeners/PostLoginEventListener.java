package org.akadia.prometheus.listeners;

import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PostLoginEventListener extends CountableMetrics {

    public PostLoginEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPostLoginEvent(PostLoginEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "total post logins";
    }

    @Override
    public String getConfigKey() {
        return "post_login_total";
    }
}
