package org.akadia.prometheus.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerChatEventListener extends CountableMetrics {

    public PlayerChatEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @Subscribe
    public void onPlayerDisconnectEvent(PlayerChatEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player chats in Velocity";
    }

    @Override
    public String getConfigKey() {
        return "player_chats";
    }
}
