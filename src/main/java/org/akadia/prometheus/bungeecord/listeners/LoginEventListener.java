package org.akadia.prometheus.bungeecord.listeners;

import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class LoginEventListener extends CountableMetrics implements Listener {

    public LoginEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onLoginEvent(LoginEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player logins in Bungeecord";
    }

    @Override
    public String getConfigKey() {
        return "player_connects";
    }

}
