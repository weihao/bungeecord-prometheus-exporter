package org.akadia.prometheus.bungeecord.metrics;

import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class RedisBungeeOnlinePlayers extends GauageMetric {

    public RedisBungeeOnlinePlayers(PrometheusExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        RedisBungeeAPI.getRedisBungeeApi()
                .getAllProxies()
                .forEach(proxy -> this.getGauge()
                        .labels(proxy)
                        .set(RedisBungeeAPI.getRedisBungeeApi()
                                .getPlayersOnProxy(proxy)
                                .size()));
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
