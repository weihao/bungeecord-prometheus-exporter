package org.akadia.prometheus.bungeecord;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.ClientConnectEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.akadia.prometheus.core.metrics.EventMetrics;

/**
 * Translates BungeeCord events into counter increments.
 *
 * <p>Every handler runs at {@link EventPriority#HIGHEST}. BungeeCord has no observe-only
 * MONITOR priority, and HIGHEST is the one that runs last, so a login denied by an auth
 * plugin is already marked cancelled by the time it is counted. Nothing here mutates an
 * event.
 */
final class BungeeCordEventListener implements Listener {

    private final EventMetrics events;

    BungeeCordEventListener(EventMetrics events) {
        this.events = events;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClientConnect(ClientConnectEvent event) {
        events.recordConnection();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        events.recordLogin(!event.isCancelled());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDisconnect(PlayerDisconnectEvent event) {
        events.recordDisconnect();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        // BungeeCord routes chat and commands through the same event.
        if (event.isCommand()) {
            events.recordCommand(event.getMessage());
        } else {
            events.recordChat();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerKick(ServerKickEvent event) {
        ServerInfo kickedFrom = event.getKickedFrom();
        events.recordKick(kickedFrom == null ? null : kickedFrom.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerSwitch(ServerSwitchEvent event) {
        ServerInfo from = event.getFrom();
        events.recordServerSwitch(
                from == null ? null : from.getName(),
                currentServerName(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProxyPing(ProxyPingEvent event) {
        events.recordListPing();
    }

    private static String currentServerName(ProxiedPlayer player) {
        if (player == null) {
            return null;
        }
        Server server = player.getServer();
        return server == null || server.getInfo() == null ? null : server.getInfo().getName();
    }
}
