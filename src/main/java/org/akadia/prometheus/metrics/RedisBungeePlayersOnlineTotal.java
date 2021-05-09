package org.akadia.prometheus.metrics;

import com.google.common.collect.Multimap;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.GauageMetric;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class RedisBungeePlayersOnlineTotal extends GauageMetric {

    public RedisBungeePlayersOnlineTotal(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        Multimap<String, UUID> servers = RedisBungee.getApi().getServerToPlayers();

        for (String key : servers.keySet()) {
            this.getGauge().labels(key).set(servers.get(key).size());
        }
    }

    @Override
    public String getConfigKey() {
        return "redis_players_online_total";
    }

    @Override
    public String getHelp() {
        return "unique redisbungee players";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
