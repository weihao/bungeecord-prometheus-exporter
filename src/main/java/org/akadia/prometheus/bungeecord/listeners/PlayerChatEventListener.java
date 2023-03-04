package org.akadia.prometheus.bungeecord.listeners;

import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class PlayerChatEventListener extends CountableMetrics implements Listener {

    public PlayerChatEventListener(PrometheusExporter plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerChatEvent(ChatEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getHelp() {
        return "the number of player chat in BungeeCord";
    }

    @Override
    public String getConfigKey() {
        return "player_chats";
    }
}
