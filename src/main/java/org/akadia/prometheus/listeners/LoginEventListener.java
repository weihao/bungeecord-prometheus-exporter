package org.akadia.prometheus.listeners;

import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class LoginEventListener extends CountableMetrics {

    public LoginEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onLoginEvent(LoginEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "total logins";
    }

    @Override
    public String getConfigKey() {
        return "login_total";
    }

}
