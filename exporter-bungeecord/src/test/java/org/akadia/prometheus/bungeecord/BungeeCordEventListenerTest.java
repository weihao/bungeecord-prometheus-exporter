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
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives the listener with mocked BungeeCord events and asserts on the resulting metrics.
 *
 * <p>Uses a real exporter rather than a mock {@code EventMetrics}, so the assertions are
 * on the exposition output that Prometheus would actually receive.
 */
class BungeeCordEventListenerTest {

    private static final String NS = "minecraft_proxy_";

    private ExporterHarness harness;
    private BungeeCordEventListener listener;

    @BeforeEach
    void setUp() {
        harness = ExporterHarness.start(new FakeProxyPlatform(),
                builder -> builder.enable(MetricId.COMMAND_LABEL));
        listener = new BungeeCordEventListener(harness.exporter().events());
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    @DisplayName("a client connect is counted")
    void clientConnect() {
        listener.onClientConnect(mock(ClientConnectEvent.class));

        assertThat(scrape().value(NS + "connections_total")).contains(1.0d);
    }

    @Test
    @DisplayName("a login is counted as allowed or denied from the event's cancelled state")
    void login() {
        LoginEvent allowed = mock(LoginEvent.class);
        when(allowed.isCancelled()).thenReturn(false);
        LoginEvent denied = mock(LoginEvent.class);
        when(denied.isCancelled()).thenReturn(true);

        listener.onLogin(allowed);
        listener.onLogin(denied);

        ScrapedMetrics scrape = scrape();
        assertThat(scrape.value(NS + "logins_total", "result", "allowed")).contains(1.0d);
        assertThat(scrape.value(NS + "logins_total", "result", "denied")).contains(1.0d);
    }

    @Test
    @DisplayName("a disconnect is counted")
    void disconnect() {
        listener.onDisconnect(mock(PlayerDisconnectEvent.class));

        assertThat(scrape().value(NS + "disconnects_total")).contains(1.0d);
    }

    @Test
    @DisplayName("BungeeCord's shared chat event is split into chat and commands")
    void chatAndCommandsAreSeparated() {
        // BungeeCord routes both through ChatEvent, distinguished only by isCommand().
        ChatEvent chat = mock(ChatEvent.class);
        when(chat.isCommand()).thenReturn(false);
        ChatEvent command = mock(ChatEvent.class);
        when(command.isCommand()).thenReturn(true);
        when(command.getMessage()).thenReturn("/tp Notch");

        listener.onChat(chat);
        listener.onChat(command);

        ScrapedMetrics scrape = scrape();
        assertThat(scrape.value(NS + "chat_messages_total")).contains(1.0d);
        assertThat(scrape.value(NS + "commands_total", "command", "tp")).contains(1.0d);
    }

    @Test
    @DisplayName("a kick records the server the player was kicked from")
    void kick() {
        ServerKickEvent event = mock(ServerKickEvent.class);
        ServerInfo kickedFrom = mock(ServerInfo.class);
        when(kickedFrom.getName()).thenReturn("survival");
        when(event.getKickedFrom()).thenReturn(kickedFrom);

        listener.onServerKick(event);

        assertThat(scrape().value(NS + "kicks_total", "server", "survival")).contains(1.0d);
    }

    @Test
    @DisplayName("a kick with no origin server is still counted, as 'unknown'")
    void kickWithoutServer() {
        ServerKickEvent event = mock(ServerKickEvent.class);
        when(event.getKickedFrom()).thenReturn(null);

        listener.onServerKick(event);

        assertThat(scrape().value(NS + "kicks_total", "server", "unknown")).contains(1.0d);
    }

    @Test
    @DisplayName("a server switch records both ends")
    void serverSwitch() {
        ServerSwitchEvent event = mock(ServerSwitchEvent.class);
        ServerInfo from = mock(ServerInfo.class);
        when(from.getName()).thenReturn("lobby");
        when(event.getFrom()).thenReturn(from);

        ProxiedPlayer player = mock(ProxiedPlayer.class);
        ServerInfo to = mock(ServerInfo.class);
        when(to.getName()).thenReturn("survival");
        Server server = mock(Server.class);
        when(server.getInfo()).thenReturn(to);
        when(player.getServer()).thenReturn(server);
        when(event.getPlayer()).thenReturn(player);

        listener.onServerSwitch(event);

        assertThat(scrape().value(NS + "server_switches_total", "from", "lobby", "to", "survival"))
                .contains(1.0d);
    }

    @Test
    @DisplayName("the first connection has no origin server and is recorded as 'unknown'")
    void initialServerConnect() {
        ServerSwitchEvent event = mock(ServerSwitchEvent.class);
        when(event.getFrom()).thenReturn(null);
        when(event.getPlayer()).thenReturn(null);

        listener.onServerSwitch(event);

        assertThat(scrape().value(NS + "server_switches_total", "from", "unknown", "to", "unknown"))
                .contains(1.0d);
    }

    @Test
    @DisplayName("a server-list ping is counted")
    void proxyPing() {
        listener.onProxyPing(mock(ProxyPingEvent.class));

        assertThat(scrape().value(NS + "list_pings_total")).contains(1.0d);
    }

    @Test
    @DisplayName("every handler observes at the last priority and mutates nothing")
    void handlersObserveOnly() {
        // Counting at an earlier priority would record decisions other plugins have not
        // made yet: a login cancelled by an auth plugin would be counted as allowed.
        assertThat(BungeeCordEventListener.class).isAssignableTo(Listener.class);

        for (Method method : BungeeCordEventListener.class.getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) {
                continue;
            }
            assertThat(annotation.priority())
                    .as("%s must observe after every other plugin", method.getName())
                    .isEqualTo(EventPriority.HIGHEST);
        }
    }

    @Test
    @DisplayName("all seven event types are handled")
    void everyEventTypeIsCovered() {
        long handlers = java.util.Arrays.stream(BungeeCordEventListener.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .count();

        assertThat(handlers).isEqualTo(7);
    }

    private ScrapedMetrics scrape() {
        return harness.scrape();
    }
}
