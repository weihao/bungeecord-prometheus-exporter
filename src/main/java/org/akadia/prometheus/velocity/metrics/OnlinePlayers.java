package org.akadia.prometheus.velocity.metrics;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.akadia.prometheus.interfaces.GauageMetric;
import org.akadia.prometheus.velocity.PrometheusVelocityExporter;

import java.util.Collection;

public class OnlinePlayers extends GauageMetric {

    public OnlinePlayers(PrometheusVelocityExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        Collection<RegisteredServer> servers = ((PrometheusVelocityExporter) getPlugin()).getProxyServer().getAllServers();
        for (RegisteredServer server : servers) {
            this.getGauge().labels(server.getServerInfo().getName()).set(server.getPlayersConnected().size());
        }
    }

    @Override
    public String getConfigKey() {
        return "online_players";
    }

    @Override
    public String getHelp() {
        return "the number of online players in BungeeCord";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server"};
    }
}
