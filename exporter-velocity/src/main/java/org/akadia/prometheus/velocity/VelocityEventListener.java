package org.akadia.prometheus.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.ConnectionHandshakeEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.akadia.prometheus.core.metrics.EventMetrics;

import java.util.Optional;

/**
 * Translates Velocity events into counter increments.
 *
 * <p>Every handler is {@link PostOrder#LAST} so that results reflect what other plugins
 * decided - a login denied by an auth plugin is counted as denied, not allowed. Handlers
 * are synchronous and do nothing but increment, which keeps them off Velocity's critical
 * path; anything slower would have to return an {@code EventTask}.
 */
final class VelocityEventListener {

    private final EventMetrics events;

    VelocityEventListener(EventMetrics events) {
        this.events = events;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onHandshake(ConnectionHandshakeEvent event) {
        events.recordConnection();
    }

    @Subscribe(order = PostOrder.LAST)
    public void onLogin(LoginEvent event) {
        events.recordLogin(event.getResult().isAllowed());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onDisconnect(DisconnectEvent event) {
        events.recordDisconnect();
    }

    @Subscribe(order = PostOrder.LAST)
    public void onChat(PlayerChatEvent event) {
        events.recordChat();
    }

    @Subscribe(order = PostOrder.LAST)
    public void onCommand(CommandExecuteEvent event) {
        events.recordCommand(event.getCommand());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onKick(KickedFromServerEvent event) {
        RegisteredServer server = event.getServer();
        events.recordKick(server == null ? null : server.getServerInfo().getName());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerConnected(ServerConnectedEvent event) {
        Optional<RegisteredServer> previous = event.getPreviousServer();
        events.recordServerSwitch(
                previous.map(server -> server.getServerInfo().getName()).orElse(null),
                event.getServer() == null ? null : event.getServer().getServerInfo().getName());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onProxyPing(ProxyPingEvent event) {
        events.recordListPing();
    }
}
