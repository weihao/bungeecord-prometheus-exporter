package org.akadia.prometheus.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerCommandEventListener extends CountableMetrics {

    public PlayerCommandEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @Subscribe
    public void onPlayerCommandEvent(CommandExecuteEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player commands in Velocity";
    }

    @Override
    public String getConfigKey() {
        return "player_commands";
    }
}
