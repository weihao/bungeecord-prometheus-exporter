package org.akadia.prometheus.bungeecord.metrics;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class RedisBungeeOnlinePlayers extends GauageMetric {

    public RedisBungeeOnlinePlayers(PrometheusExporter plugin) {
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
        return "the number of online redisbungee players";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
