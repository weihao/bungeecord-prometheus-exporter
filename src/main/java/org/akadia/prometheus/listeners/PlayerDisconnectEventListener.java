package org.akadia.prometheus.listeners;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerDisconnectEventListener extends CountableMetrics {

    public PlayerDisconnectEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getConfigKey() {
        return "player_disconnects_total";
    }

    @Override
    public String getHelp() {
        return "total player disconnects";
    }
}
