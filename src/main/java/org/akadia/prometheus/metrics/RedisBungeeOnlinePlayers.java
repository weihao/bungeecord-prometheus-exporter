package org.akadia.prometheus.metrics;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.GauageMetric;

public class RedisBungeeOnlinePlayers extends GauageMetric {

    public RedisBungeeOnlinePlayers(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        for (String key : RedisBungee.getApi().getAllServers()) {
            int size = RedisBungee.getApi().getPlayersOnServer(key).size();
            this.getGauge().labels(key).set(size);
        }
    }

    @Override
    public String getConfigKey() {
        return "redis_online_players";
    }

    @Override
    public String getHelp() {
        return "online redisbungee players";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
