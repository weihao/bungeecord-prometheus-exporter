package org.akadia.prometheus.bungeecord.metrics;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class RedisBungeeOnlineProxies extends GauageMetric {

    public RedisBungeeOnlineProxies(PrometheusExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().set(RedisBungee.getApi().getAllServers().size());
    }

    @Override
    public String getConfigKey() {
        return "redis_bungee_online_proxies";
    }

    @Override
    public String getHelp() {
        return "the number of online redisbungee proxy";
    }
}
