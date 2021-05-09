package org.akadia.prometheus.metrics;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.GauageMetric;

public class RedisBungeeProxyOnlineTotal extends GauageMetric {

    public RedisBungeeProxyOnlineTotal(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().set(RedisBungee.getApi().getAllServers().size());
    }

    @Override
    public String getConfigKey() {
        return "redis_bungee_proxies_online_total";
    }

    @Override
    public String getHelp() {
        return "total redisbungee online proxy";
    }
}
