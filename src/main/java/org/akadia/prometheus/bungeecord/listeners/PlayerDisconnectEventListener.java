package org.akadia.prometheus.bungeecord.listeners;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerDisconnectEventListener extends CountableMetrics implements Listener {

    public PlayerDisconnectEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player disconnects in Bungeecord";
    }

    @Override
    public String getConfigKey() {
        return "player_disconnects";
    }
}
