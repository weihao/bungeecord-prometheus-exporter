package org.akadia.prometheus.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.ProxyVersion;
import org.akadia.prometheus.core.platform.BackendPing;
import org.akadia.prometheus.core.platform.BackendServerHandle;
import org.akadia.prometheus.core.platform.PlatformLogger;
import org.akadia.prometheus.core.platform.PlayerSnapshot;
import org.akadia.prometheus.core.platform.ProxyIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the Velocity API is mapped onto the platform SPI correctly. */
class VelocityPlatformTest {

    private ProxyServer proxy;
    private PluginManager pluginManager;
    private List<RegisteredServer> servers;

    @BeforeEach
    void setUp() {
        proxy = mock(ProxyServer.class);
        pluginManager = mock(PluginManager.class);
        servers = new ArrayList<>();

        ProxyConfig config = mock(ProxyConfig.class);
        when(config.isOnlineMode()).thenReturn(false);
        when(proxy.getConfiguration()).thenReturn(config);
        when(proxy.getVersion()).thenReturn(
                new ProxyVersion("Velocity", "PaperMC", "3.4.0-SNAPSHOT"));
        when(proxy.getPluginManager()).thenReturn(pluginManager);
        when(proxy.getAllServers()).thenReturn(servers);
        when(proxy.getAllPlayers()).thenReturn(List.of());
        when(pluginManager.getPlugins()).thenReturn(List.of());
    }

    @Test
    @DisplayName("identity is read once from the proxy")
    void identity() {
        ProxyIdentity identity = platform().identity();

        assertThat(identity.type()).isEqualTo(ProxyIdentity.TYPE_VELOCITY);
        assertThat(identity.name()).isEqualTo("Velocity");
        assertThat(identity.version()).isEqualTo("3.4.0-SNAPSHOT");
        assertThat(identity.onlineMode()).isFalse();
    }

    @Test
    @DisplayName("the online count uses the proxy's own counter")
    void onlineCount() {
        when(proxy.getPlayerCount()).thenReturn(512);

        assertThat(platform().onlinePlayerCount()).isEqualTo(512);
    }

    @Test
    @DisplayName("players carry uuid, name, server and ping")
    void players() {
        UUID uuid = UUID.randomUUID();
        // Mocks are built before the stubbing starts: Mockito rejects mock creation that
        // happens while another when(...) is still open.
        Player notch = player(uuid, "Notch", "lobby", 42L);
        when(proxy.getAllPlayers()).thenReturn(List.of(notch));

        List<PlayerSnapshot> snapshots = platform().players();

        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.uuid()).isEqualTo(uuid);
            assertThat(snapshot.username()).isEqualTo("Notch");
            assertThat(snapshot.serverName()).isEqualTo("lobby");
            assertThat(snapshot.pingSeconds()).isEqualTo(0.042d);
        });
    }

    @Test
    @DisplayName("a player with no current server is reported without one")
    void playerWithoutServer() {
        Player connecting = mock(Player.class);
        when(connecting.getUniqueId()).thenReturn(UUID.randomUUID());
        when(connecting.getUsername()).thenReturn("Connecting");
        when(connecting.getPing()).thenReturn(15L);
        when(connecting.getCurrentServer()).thenReturn(Optional.empty());
        when(proxy.getAllPlayers()).thenReturn(List.of(connecting));

        assertThat(platform().players()).singleElement().satisfies(snapshot ->
                assertThat(snapshot.serverNameOrNone()).isEqualTo(PlayerSnapshot.NO_SERVER));
    }

    @Test
    @DisplayName("a player that vanished mid-iteration is skipped rather than thrown on")
    void racyPlayerIsSkipped() {
        Player departed = mock(Player.class);
        when(departed.getUniqueId()).thenReturn(null);
        when(departed.getUsername()).thenReturn(null);
        Player survivor = player(UUID.randomUUID(), "Survivor", "lobby", 20L);
        when(proxy.getAllPlayers()).thenReturn(java.util.Arrays.asList(departed, survivor));

        assertThat(platform().players()).extracting(PlayerSnapshot::username)
                .containsExactly("Survivor");
    }

    @Test
    @DisplayName("servers are exposed with name and address")
    void serverHandles() {
        servers.add(registeredServer("lobby", "10.0.0.5", 25566, 4));

        List<BackendServerHandle> handles = platform().servers();

        assertThat(handles).singleElement().satisfies(handle -> {
            assertThat(handle.name()).isEqualTo("lobby");
            assertThat(handle.address()).isEqualTo("10.0.0.5:25566");
            assertThat(handle.connectedPlayers()).isEqualTo(4);
        });
    }

    @Test
    @Timeout(10)
    @DisplayName("a ping response is mapped to the platform type")
    void pingSuccess() throws Exception {
        RegisteredServer server = registeredServer("lobby", "10.0.0.5", 25566, 0);
        ServerPing response = new ServerPing(
                new ServerPing.Version(767, "1.21.4"),
                new ServerPing.Players(7, 100, List.of()),
                net.kyori.adventure.text.Component.text("A server"),
                null);
        when(server.ping()).thenReturn(CompletableFuture.completedFuture(response));
        servers.add(server);

        BackendPing ping = platform().servers().get(0).ping().get(5, TimeUnit.SECONDS);

        assertThat(ping.onlinePlayers()).isEqualTo(7);
        assertThat(ping.maxPlayers()).isEqualTo(100);
        assertThat(ping.protocolVersion()).isEqualTo(767);
        assertThat(ping.versionName()).isEqualTo("1.21.4");
    }

    @Test
    @Timeout(10)
    @DisplayName("a failed ping propagates as a failed future")
    void pingFailure() {
        RegisteredServer server = registeredServer("dead", "10.0.0.9", 25567, 0);
        when(server.ping()).thenReturn(CompletableFuture.failedFuture(
                new java.net.ConnectException("refused")));
        servers.add(server);

        assertThat(platform().servers().get(0).ping()).failsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(10)
    @DisplayName("a ping response with no players block yields unknown counts, not a failure")
    void pingWithoutPlayersBlock() throws Exception {
        RegisteredServer server = registeredServer("quiet", "10.0.0.9", 25567, 0);
        ServerPing response = new ServerPing(
                new ServerPing.Version(767, "1.21.4"),
                null,
                net.kyori.adventure.text.Component.text("A server"),
                null);
        when(server.ping()).thenReturn(CompletableFuture.completedFuture(response));
        servers.add(server);

        BackendPing ping = platform().servers().get(0).ping().get(5, TimeUnit.SECONDS);

        assertThat(ping.onlinePlayers()).isEqualTo(BackendPing.UNKNOWN);
        assertThat(ping.maxPlayers()).isEqualTo(BackendPing.UNKNOWN);
        assertThat(ping.versionName()).isEqualTo("1.21.4");
    }

    @Test
    @DisplayName("plugins are read from the plugin manager, keyed by id")
    void plugins() {
        PluginContainer container = mock(PluginContainer.class);
        PluginDescription description = mock(PluginDescription.class);
        when(description.getId()).thenReturn("some-plugin");
        when(description.getVersion()).thenReturn(Optional.of("2.1.0"));
        when(container.getDescription()).thenReturn(description);
        when(pluginManager.getPlugins()).thenReturn(List.of(container));

        assertThat(platform().plugins()).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.id()).isEqualTo("some-plugin");
            assertThat(descriptor.version()).isEqualTo("2.1.0");
        });
    }

    @Test
    @DisplayName("a plugin with no declared version reports 'unknown'")
    void pluginWithoutVersion() {
        PluginContainer container = mock(PluginContainer.class);
        PluginDescription description = mock(PluginDescription.class);
        when(description.getId()).thenReturn("no-version");
        when(description.getVersion()).thenReturn(Optional.empty());
        when(container.getDescription()).thenReturn(description);
        when(pluginManager.getPlugins()).thenReturn(List.of(container));

        assertThat(platform().plugins()).singleElement()
                .satisfies(descriptor -> assertThat(descriptor.version()).isEqualTo("unknown"));
    }

    private VelocityPlatform platform() {
        return new VelocityPlatform(proxy, PlatformLogger.noop());
    }

    private static Player player(UUID uuid, String username, String serverName, long ping) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn(username);
        when(player.getPing()).thenReturn(ping);

        ServerConnection connection = mock(ServerConnection.class);
        when(connection.getServerInfo()).thenReturn(
                new ServerInfo(serverName, InetSocketAddress.createUnresolved("host", 25565)));
        when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        return player;
    }

    private static RegisteredServer registeredServer(String name, String host, int port, int players) {
        List<Player> connected = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            connected.add(mock(Player.class));
        }
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(
                new ServerInfo(name, InetSocketAddress.createUnresolved(host, port)));
        when(server.getPlayersConnected()).thenReturn(connected);
        return server;
    }
}
