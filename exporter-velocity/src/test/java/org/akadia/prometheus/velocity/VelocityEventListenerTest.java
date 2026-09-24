package org.akadia.prometheus.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
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
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Drives the listener with mocked Velocity events and asserts on the resulting metrics. */
class VelocityEventListenerTest {

    private static final String NS = "minecraft_proxy_";

    private ExporterHarness harness;
    private VelocityEventListener listener;

    @BeforeEach
    void setUp() {
        harness = ExporterHarness.start(new FakeProxyPlatform(),
                builder -> builder.enable(MetricId.COMMAND_LABEL));
        listener = new VelocityEventListener(harness.exporter().events());
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    @DisplayName("a handshake is counted as a connection")
    void handshake() {
        listener.onHandshake(mock(ConnectionHandshakeEvent.class));

        assertThat(scrape().value(NS + "connections_total")).contains(1.0d);
    }

    @Test
    @DisplayName("a login is counted as allowed or denied from the event result")
    void login() {
        LoginEvent allowed = mock(LoginEvent.class);
        when(allowed.getResult()).thenReturn(ResultedEvent.ComponentResult.allowed());
        LoginEvent denied = mock(LoginEvent.class);
        when(denied.getResult()).thenReturn(ResultedEvent.ComponentResult.denied(
                net.kyori.adventure.text.Component.text("nope")));

        listener.onLogin(allowed);
        listener.onLogin(denied);

        ScrapedMetrics scrape = scrape();
        assertThat(scrape.value(NS + "logins_total", "result", "allowed")).contains(1.0d);
        assertThat(scrape.value(NS + "logins_total", "result", "denied")).contains(1.0d);
    }

    @Test
    @DisplayName("a disconnect is counted")
    void disconnect() {
        listener.onDisconnect(mock(DisconnectEvent.class));

        assertThat(scrape().value(NS + "disconnects_total")).contains(1.0d);
    }

    @Test
    @DisplayName("chat and commands are separate events on Velocity and stay separate metrics")
    void chatAndCommands() {
        listener.onChat(mock(PlayerChatEvent.class));

        CommandExecuteEvent command = mock(CommandExecuteEvent.class);
        when(command.getCommand()).thenReturn("gamemode creative");
        listener.onCommand(command);

        ScrapedMetrics scrape = scrape();
        assertThat(scrape.value(NS + "chat_messages_total")).contains(1.0d);
        assertThat(scrape.value(NS + "commands_total", "command", "gamemode")).contains(1.0d);
    }

    @Test
    @DisplayName("a kick records the server the player was kicked from")
    void kick() {
        // Mocks are built before the stubbing starts: Mockito rejects mock creation that
        // happens while another when(...) is still open.
        RegisteredServer survival = server("survival");
        KickedFromServerEvent event = mock(KickedFromServerEvent.class);
        when(event.getServer()).thenReturn(survival);

        listener.onKick(event);

        assertThat(scrape().value(NS + "kicks_total", "server", "survival")).contains(1.0d);
    }

    @Test
    @DisplayName("a server connect records the previous server when there is one")
    void serverSwitch() {
        RegisteredServer lobby = server("lobby");
        RegisteredServer survival = server("survival");
        ServerConnectedEvent event = mock(ServerConnectedEvent.class);
        when(event.getPreviousServer()).thenReturn(Optional.of(lobby));
        when(event.getServer()).thenReturn(survival);

        listener.onServerConnected(event);

        assertThat(scrape().value(NS + "server_switches_total", "from", "lobby", "to", "survival"))
                .contains(1.0d);
    }

    @Test
    @DisplayName("the initial connect has no previous server and is recorded as 'unknown'")
    void initialServerConnect() {
        RegisteredServer lobby = server("lobby");
        ServerConnectedEvent event = mock(ServerConnectedEvent.class);
        when(event.getPreviousServer()).thenReturn(Optional.empty());
        when(event.getServer()).thenReturn(lobby);

        listener.onServerConnected(event);

        assertThat(scrape().value(NS + "server_switches_total", "from", "unknown", "to", "lobby"))
                .contains(1.0d);
    }

    @Test
    @DisplayName("a server-list ping is counted")
    void proxyPing() {
        listener.onProxyPing(mock(ProxyPingEvent.class));

        assertThat(scrape().value(NS + "list_pings_total")).contains(1.0d);
    }

    @Test
    @DisplayName("every handler subscribes at PostOrder.LAST so other plugins' results are final")
    void handlersObserveLast() {
        for (Method method : VelocityEventListener.class.getDeclaredMethods()) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) {
                continue;
            }
            assertThat(annotation.order())
                    .as("%s must observe after every other plugin", method.getName())
                    .isEqualTo(PostOrder.LAST);
        }
    }

    @Test
    @DisplayName("all eight event types are handled")
    void everyEventTypeIsCovered() {
        long handlers = Arrays.stream(VelocityEventListener.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Subscribe.class))
                .count();

        assertThat(handlers).isEqualTo(8);
    }

    private static RegisteredServer server(String name) {
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(
                new ServerInfo(name, InetSocketAddress.createUnresolved("host", 25565)));
        return server;
    }

    private ScrapedMetrics scrape() {
        return harness.scrape();
    }
}
