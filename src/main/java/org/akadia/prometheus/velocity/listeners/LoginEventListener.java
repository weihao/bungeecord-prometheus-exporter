package org.akadia.prometheus.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class LoginEventListener extends CountableMetrics {

    public LoginEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @Subscribe
    public void onLoginEvent(LoginEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player logins in Velocity";
    }

    @Override
    public String getConfigKey() {
        return "player_connects";
    }

}
