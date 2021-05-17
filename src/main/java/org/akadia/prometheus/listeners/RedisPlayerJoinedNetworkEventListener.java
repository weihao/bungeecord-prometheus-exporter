package org.akadia.prometheus.listeners;

import com.imaginarycode.minecraft.redisbungee.events.PlayerJoinedNetworkEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class RedisPlayerJoinedNetworkEventListener extends CountableMetrics {

    public RedisPlayerJoinedNetworkEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerJoinedNetworkEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getConfigKey() {
        return "redis_player_joins_total";
    }

    @Override
    public String getHelp() {
        return "total redis bungee player joins";
    }
}
