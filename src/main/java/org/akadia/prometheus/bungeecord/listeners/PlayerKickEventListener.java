package org.akadia.prometheus.bungeecord.listeners;

import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerKickEventListener extends CountableMetrics implements Listener {

    public PlayerKickEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerDisconnectEvent(ServerKickEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player kicked in BungeeCord";
    }

    @Override
    public String getConfigKey() {
        return "player_kicks";
    }
}
