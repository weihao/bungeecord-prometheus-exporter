package org.akadia.prometheus.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerDisconnectEventListener extends CountableMetrics {

    public PlayerDisconnectEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @Subscribe
    public void onPlayerDisconnectEvent(DisconnectEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player disconnects in Velocity";
    }

    @Override
    public String getConfigKey() {
        return "player_disconnects";
    }
}
