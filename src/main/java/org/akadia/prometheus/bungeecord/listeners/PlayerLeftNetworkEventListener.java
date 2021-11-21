package org.akadia.prometheus.bungeecord.listeners;

import com.imaginarycode.minecraft.redisbungee.events.PlayerLeftNetworkEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerLeftNetworkEventListener extends CountableMetrics implements Listener {

    public PlayerLeftNetworkEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerLeftNetworkEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of players disconnects in redisbungee";
    }

    @Override
    public String getConfigKey() {
        return "redis_player_disconnects";
    }
}
