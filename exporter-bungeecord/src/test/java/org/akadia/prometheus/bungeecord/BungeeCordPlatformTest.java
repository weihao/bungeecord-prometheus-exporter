package org.akadia.prometheus.bungeecord;

import net.md_5.bungee.api.ProxyConfig;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.PluginManager;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the BungeeCord API is mapped onto the platform SPI correctly. */
class BungeeCordPlatformTest {

    private ProxyServer proxy;
    private PluginManager pluginManager;
    private Map<String, ServerInfo> servers;

    @BeforeEach
    void setUp() {
        proxy = mock(ProxyServer.class);
        pluginManager = mock(PluginManager.class);
        servers = new LinkedHashMap<>();

        ProxyConfig config = mock(ProxyConfig.class);
        when(config.isOnlineMode()).thenReturn(true);
        when(proxy.getConfig()).thenReturn(config);
        when(proxy.getName()).thenReturn("Waterfall");
        when(proxy.getVersion()).thenReturn("1.21-R0.4-SNAPSHOT");
        when(proxy.getPluginManager()).thenReturn(pluginManager);
        when(proxy.getServers()).thenReturn(servers);
        when(proxy.getPlayers()).thenReturn(List.of());
        when(pluginManager.getPlugins()).thenReturn(List.of());
    }

    @Test
    @DisplayName("identity is read once from the proxy")
    void identity() {
        ProxyIdentity identity = platform().identity();

        assertThat(identity.type()).isEqualTo(ProxyIdentity.TYPE_BUNGEECORD);
        assertThat(identity.name()).isEqualTo("Waterfall");
        assertThat(identity.version()).isEqualTo("1.21-R0.4-SNAPSHOT");
        assertThat(identity.onlineMode()).isTrue();
    }

    @Test
    @DisplayName("the online count uses the proxy's own counter, not the player list")
    void onlineCount() {
        when(proxy.getOnlineCount()).thenReturn(137);

        assertThat(platform().onlinePlayerCount()).isEqualTo(137);
    }

    @Test
    @DisplayName("players carry uuid, name, server and ping")
    void players() {
        UUID uuid = UUID.randomUUID();
        // Mocks are built before the stubbing starts: Mockito rejects mock creation that
        // happens while another when(...) is still open.
        ProxiedPlayer notch = player(uuid, "Notch", "lobby", 42);
        when(proxy.getPlayers()).thenReturn(List.of(notch));

        List<PlayerSnapshot> snapshots = platform().players();

        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.uuid()).isEqualTo(uuid);
            assertThat(snapshot.username()).isEqualTo("Notch");
            assertThat(snapshot.serverName()).isEqualTo("lobby");
            assertThat(snapshot.pingMillis()).isEqualTo(42);
        });
    }

    @Test
    @DisplayName("a player with no server yet is reported without one rather than skipped")
    void playerWithoutServer() {
        // getServer() is null during the handshake window, before routing.
        ProxiedPlayer connecting = mock(ProxiedPlayer.class);
        when(connecting.getUniqueId()).thenReturn(UUID.randomUUID());
        when(connecting.getName()).thenReturn("Connecting");
        when(connecting.getServer()).thenReturn(null);
        when(connecting.getPing()).thenReturn(15);
        when(proxy.getPlayers()).thenReturn(List.of(connecting));

        List<PlayerSnapshot> snapshots = platform().players();

        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.serverName()).isNull();
            assertThat(snapshot.serverNameOrNone()).isEqualTo(PlayerSnapshot.NO_SERVER);
        });
    }

    @Test
    @DisplayName("a player that disconnected mid-iteration is skipped, not thrown on")
    void racyPlayerIsSkipped() {
        // getPlayers() hands back a live collection; by the time it is read, accessors on a
        // departed player can return null.
        ProxiedPlayer departed = mock(ProxiedPlayer.class);
        when(departed.getUniqueId()).thenReturn(null);
        when(departed.getName()).thenReturn(null);

        ProxiedPlayer survivor = player(UUID.randomUUID(), "Survivor", "lobby", 20);
        when(proxy.getPlayers()).thenReturn(java.util.Arrays.asList(departed, survivor));

        assertThat(platform().players()).extracting(PlayerSnapshot::username)
                .containsExactly("Survivor");
    }

    @Test
    @DisplayName("servers are exposed with name and address")
    void serverHandles() {
        servers.put("lobby", serverInfo("lobby", "10.0.0.5", 25566, 3));

        List<BackendServerHandle> handles = platform().servers();

        assertThat(handles).singleElement().satisfies(handle -> {
            assertThat(handle.name()).isEqualTo("lobby");
            assertThat(handle.address()).isEqualTo("10.0.0.5:25566");
            assertThat(handle.connectedPlayers()).isEqualTo(3);
        });
    }

    @Test
    @Timeout(10)
    @DisplayName("the callback-based ping is adapted into a completed future")
    void pingSuccess() throws Exception {
        ServerInfo info = serverInfo("lobby", "10.0.0.5", 25566, 0);
        ServerPing response = pingResponse(7, 100, 767, "1.21.4");
        org.mockito.Mockito.doAnswer(invocation -> {
            net.md_5.bungee.api.Callback<ServerPing> callback = invocation.getArgument(0);
            callback.done(response, null);
            return null;
        }).when(info).ping(org.mockito.ArgumentMatchers.any());
        servers.put("lobby", info);

        BackendPing ping = platform().servers().get(0).ping().get(5, TimeUnit.SECONDS);

        assertThat(ping.onlinePlayers()).isEqualTo(7);
        assertThat(ping.maxPlayers()).isEqualTo(100);
        assertThat(ping.protocolVersion()).isEqualTo(767);
        assertThat(ping.versionName()).isEqualTo("1.21.4");
    }

    @Test
    @Timeout(10)
    @DisplayName("a ping error completes the future exceptionally rather than hanging")
    void pingFailure() {
        ServerInfo info = serverInfo("dead", "10.0.0.9", 25567, 0);
        org.mockito.Mockito.doAnswer(invocation -> {
            net.md_5.bungee.api.Callback<ServerPing> callback = invocation.getArgument(0);
            callback.done(null, new java.net.ConnectException("refused"));
            return null;
        }).when(info).ping(org.mockito.ArgumentMatchers.any());
        servers.put("dead", info);

        CompletableFuture<BackendPing> future = platform().servers().get(0).ping();

        assertThat(future).failsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(10)
    @DisplayName("a null ping response is treated as a failure, not as an empty success")
    void pingNullResponse() {
        ServerInfo info = serverInfo("odd", "10.0.0.9", 25567, 0);
        org.mockito.Mockito.doAnswer(invocation -> {
            net.md_5.bungee.api.Callback<ServerPing> callback = invocation.getArgument(0);
            callback.done(null, null);
            return null;
        }).when(info).ping(org.mockito.ArgumentMatchers.any());
        servers.put("odd", info);

        assertThat(platform().servers().get(0).ping()).failsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("a ping response missing its players block still yields a usable result")
    void pingWithoutPlayersBlock() throws Exception {
        ServerInfo info = serverInfo("quiet", "10.0.0.9", 25567, 0);
        ServerPing sparse = mock(ServerPing.class);
        when(sparse.getPlayers()).thenReturn(null);
        when(sparse.getVersion()).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            net.md_5.bungee.api.Callback<ServerPing> callback = invocation.getArgument(0);
            callback.done(sparse, null);
            return null;
        }).when(info).ping(org.mockito.ArgumentMatchers.any());
        servers.put("quiet", info);

        BackendPing ping = platform().servers().get(0).ping().get(5, TimeUnit.SECONDS);

        assertThat(ping.onlinePlayers()).isEqualTo(BackendPing.UNKNOWN);
        assertThat(ping.versionName()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("plugins are read from the plugin manager")
    void plugins() {
        Plugin plugin = mock(Plugin.class);
        PluginDescription description = new PluginDescription();
        description.setName("SomePlugin");
        description.setVersion("2.1.0");
        when(plugin.getDescription()).thenReturn(description);
        when(pluginManager.getPlugins()).thenReturn(List.of(plugin));

        assertThat(platform().plugins()).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.id()).isEqualTo("SomePlugin");
            assertThat(descriptor.version()).isEqualTo("2.1.0");
        });
    }

    @Test
    @DisplayName("a plugin with a broken description is skipped rather than failing the scrape")
    void brokenPluginIsSkipped() {
        Plugin broken = mock(Plugin.class);
        when(broken.getDescription()).thenReturn(null);
        Plugin fine = mock(Plugin.class);
        PluginDescription description = new PluginDescription();
        description.setName("Fine");
        description.setVersion("1.0");
        when(fine.getDescription()).thenReturn(description);
        when(pluginManager.getPlugins()).thenReturn(java.util.Arrays.asList(broken, fine));

        assertThat(platform().plugins()).extracting(descriptor -> descriptor.id())
                .containsExactly("Fine");
    }

    @Test
    @DisplayName("a plugin with no version reports 'unknown' instead of null")
    void pluginWithoutVersion() {
        Plugin plugin = mock(Plugin.class);
        PluginDescription description = new PluginDescription();
        description.setName("NoVersion");
        when(plugin.getDescription()).thenReturn(description);
        when(pluginManager.getPlugins()).thenReturn(List.of(plugin));

        assertThat(platform().plugins()).singleElement()
                .satisfies(descriptor -> assertThat(descriptor.version()).isEqualTo("unknown"));
    }

    @Test
    @DisplayName("a server with no resolvable address does not blow up")
    void serverWithoutAddress() {
        ServerInfo info = mock(ServerInfo.class);
        when(info.getName()).thenReturn("odd");
        when(info.getAddress()).thenReturn(null);
        when(info.getPlayers()).thenReturn(List.of());
        servers.put("odd", info);

        assertThatCode(() -> assertThat(platform().servers().get(0).address())
                .isEqualTo("unknown")).doesNotThrowAnyException();
    }

    private BungeeCordPlatform platform() {
        return new BungeeCordPlatform(proxy, PlatformLogger.noop());
    }

    private static ProxiedPlayer player(UUID uuid, String name, String serverName, int ping) {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        when(player.getPing()).thenReturn(ping);

        ServerInfo info = mock(ServerInfo.class);
        when(info.getName()).thenReturn(serverName);
        Server server = mock(Server.class);
        when(server.getInfo()).thenReturn(info);
        when(player.getServer()).thenReturn(server);
        return player;
    }

    private static ServerInfo serverInfo(String name, String host, int port, int playerCount) {
        List<ProxiedPlayer> connected = new java.util.ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            connected.add(mock(ProxiedPlayer.class));
        }
        ServerInfo info = mock(ServerInfo.class);
        when(info.getName()).thenReturn(name);
        when(info.getAddress()).thenReturn(InetSocketAddress.createUnresolved(host, port));
        when(info.getPlayers()).thenReturn(connected);
        return info;
    }

    private static ServerPing pingResponse(int online, int max, int protocol, String versionName) {
        ServerPing.Players players = new ServerPing.Players(max, online, null);
        ServerPing.Protocol version = new ServerPing.Protocol(versionName, protocol);
        ServerPing response = mock(ServerPing.class);
        when(response.getPlayers()).thenReturn(players);
        when(response.getVersion()).thenReturn(version);
        return response;
    }
}
