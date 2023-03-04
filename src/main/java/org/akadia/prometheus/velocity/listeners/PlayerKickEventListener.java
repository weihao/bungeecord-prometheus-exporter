package org.akadia.prometheus.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerKickEventListener extends CountableMetrics {

    public PlayerKickEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @Subscribe
    public void onPlayerDisconnectEvent(KickedFromServerEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player kicked in Velocity";
    }

    @Override
    public String getConfigKey() {
        return "player_kicks";
    }
}
