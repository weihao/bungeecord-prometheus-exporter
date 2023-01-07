package org.akadia.prometheus.bungeecord.metrics;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.akadia.prometheus.bungeecord.PrometheusBungeeCordExporter;
import org.akadia.prometheus.interfaces.SummaryMetric;

import java.util.Collection;

public class OnlinePlayersLatency extends SummaryMetric {

    public OnlinePlayersLatency(PrometheusBungeeCordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        Collection<ProxiedPlayer> players = ((PrometheusBungeeCordExporter) getPlugin()).getProxy().getPlayers();
        for (ProxiedPlayer player : players) {
            this.getSummary().labels(player.getName()).observe(player.getPing());
        }
    }

    @Override
    public String getConfigKey() {
        return "online_player_latency";
    }

    @Override
    public String getHelp() {
        return "the latency of an online player in BungeeCord";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"name"};
    }
}
