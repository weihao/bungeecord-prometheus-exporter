package org.akadia.prometheus.listeners;

import com.imaginarycode.minecraft.redisbungee.events.PlayerLeftNetworkEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class RedisPlayerLeftNetworkEventListener extends CountableMetrics {

    public RedisPlayerLeftNetworkEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerLeftNetworkEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getConfigKey() {
        return "redis_player_disconnects_total";
    }

    @Override
    public String getHelp() {
        return "total redis bungee player disconnects";
    }
}
