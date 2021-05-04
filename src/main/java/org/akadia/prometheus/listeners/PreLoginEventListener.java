package org.akadia.prometheus.listeners;

import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PreLoginEventListener extends CountableMetrics {

    public PreLoginEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPreLoginEvent(PreLoginEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "total pre logins";
    }

    @Override
    public String getConfigKey() {
        return "pre_login_total";
    }
}
