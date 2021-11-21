package org.akadia.prometheus.bungeecord.listeners;

import com.imaginarycode.minecraft.redisbungee.events.PlayerJoinedNetworkEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerJoinedNetworkEventListener extends CountableMetrics implements Listener {

    public PlayerJoinedNetworkEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerJoinedNetworkEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of players joined in redisbungee";
    }

    @Override
    public String getConfigKey() {
        return "redis_player_connects";
    }
}
