package org.akadia.prometheus.velocity.metrics;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.akadia.prometheus.bungeecord.PrometheusBungeeCordExporter;
import org.akadia.prometheus.interfaces.SummaryMetric;
import org.akadia.prometheus.velocity.PrometheusVelocityExporter;

import java.util.Collection;

public class OnlinePlayersLatency extends SummaryMetric {

    public OnlinePlayersLatency(PrometheusVelocityExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        Collection<Player> allPlayers = ((PrometheusVelocityExporter) getPlugin()).getProxyServer().getAllPlayers();
        for (Player player : allPlayers) {
            this.getSummary().labels(player.getUsername()).observe(player.getPing());
        }
    }


    @Override
    public String getConfigKey() {
        return "online_player_latency";
    }

    @Override
    public String getHelp() {
        return "the latency of an online player in Velocity";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"name"};
    }
}
