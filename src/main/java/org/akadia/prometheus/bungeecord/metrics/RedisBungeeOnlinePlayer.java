package org.akadia.prometheus.bungeecord.metrics;

import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class RedisBungeeOnlinePlayer extends GauageMetric {

    public RedisBungeeOnlinePlayer(PrometheusExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().clear();
        RedisBungeeAPI.getRedisBungeeApi()
                .getServerToPlayers()
                .asMap()
                .forEach((server, players) -> {
                    this.getGauge()
                            .labels(server)
                            .set(0);
                    players.forEach(player -> {
                        this.getGauge()
                                .labels(server, player.toString())
                                .set(1);
                    });
                });
    }

    @Override
    public String getConfigKey() {
        return "redis_online_player";
    }

    @Override
    public String getHelp() {
        return "the name of online redisbungee player";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server", "player"};
    }
}
