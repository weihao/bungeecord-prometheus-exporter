package org.akadia.prometheus.bungeecord.metrics;

import net.md_5.bungee.api.ProxyServer;
import org.akadia.prometheus.bungeecord.PrometheusBungeeCordExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class OnlinePlayer extends GauageMetric {

    public OnlinePlayer(PrometheusBungeeCordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().clear();

        ProxyServer proxy = ((PrometheusBungeeCordExporter) getPlugin()).getProxy();
        proxy.getServers().forEach((key, value) -> {
            this.getGauge().labels(key, "").set(0);
            value.getPlayers().forEach(proxiedPlayer ->
                    this.getGauge().labels(key, proxiedPlayer.getName(), Boolean.toString(proxy.getConfig().isOnlineMode())).set(1));
        });
    }

    @Override
    public String getConfigKey() {
        return "online_player";
    }

    @Override
    public String getHelp() {
        return "the name of the online player in BungeeCord";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server", "player", "online_mode"};
    }
}
