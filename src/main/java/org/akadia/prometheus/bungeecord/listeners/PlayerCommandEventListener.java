package org.akadia.prometheus.bungeecord.listeners;

import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerCommandEventListener extends CountableMetrics implements Listener {

    public PlayerCommandEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerCommandEvent(ChatEvent event) {
        if (!event.isCommand()) {
            return;
        }
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player commands in BungeeCord";
    }

    @Override
    public String getConfigKey() {
        return "player_commands";
    }
}
