package org.akadia.prometheus.core.support;

import org.akadia.prometheus.core.platform.BackendServerHandle;
import org.akadia.prometheus.core.platform.PlatformLogger;
import org.akadia.prometheus.core.platform.PlayerSnapshot;
import org.akadia.prometheus.core.platform.PluginDescriptor;
import org.akadia.prometheus.core.platform.ProxyIdentity;
import org.akadia.prometheus.core.platform.ProxyPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * An in-memory proxy.
 *
 * <p>The reason the core module refuses a compile dependency on BungeeCord or Velocity:
 * with this in place every collector, the cardinality guard, the samplers and the HTTP
 * endpoint can be exercised end to end in a plain JVM test, including states that are
 * awkward to produce on a real proxy - a backend that hangs forever, a player with no
 * server, ten thousand distinct usernames.
 *
 * <p>Mutable and thread-safe so that soak tests can churn the world while scrapes run.
 */
public final class FakeProxyPlatform implements ProxyPlatform {

    private final List<PlayerSnapshot> players = new CopyOnWriteArrayList<>();
    private final List<BackendServerHandle> servers = new CopyOnWriteArrayList<>();
    private final List<PluginDescriptor> plugins = new CopyOnWriteArrayList<>();
    private final RecordingLogger logger = new RecordingLogger();
    private final AtomicInteger playerCountCalls = new AtomicInteger();

    private volatile ProxyIdentity identity =
            new ProxyIdentity("velocity", "Velocity", "3.4.0-TEST", true);
    private volatile Supplier<RuntimeException> playersFailure;

    @Override
    public ProxyIdentity identity() {
        return identity;
    }

    @Override
    public int onlinePlayerCount() {
        playerCountCalls.incrementAndGet();
        return players.size();
    }

    @Override
    public List<PlayerSnapshot> players() {
        Supplier<RuntimeException> failure = playersFailure;
        if (failure != null) {
            throw failure.get();
        }
        return List.copyOf(players);
    }

    @Override
    public List<BackendServerHandle> servers() {
        return List.copyOf(servers);
    }

    @Override
    public List<PluginDescriptor> plugins() {
        return List.copyOf(plugins);
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    // -- test controls ----------------------------------------------------------------

    public RecordingLogger recordingLogger() {
        return logger;
    }

    public FakeProxyPlatform identity(ProxyIdentity identity) {
        this.identity = identity;
        return this;
    }

    /** Makes {@link #players()} throw, to prove a failing collector is contained. */
    public FakeProxyPlatform failPlayersWith(Supplier<RuntimeException> failure) {
        this.playersFailure = failure;
        return this;
    }

    public FakeProxyPlatform addPlayer(String username, String server, long pingMillis) {
        players.add(new PlayerSnapshot(
                UUID.nameUUIDFromBytes(username.getBytes()), username, server, pingMillis));
        return this;
    }

    public FakeProxyPlatform addPlayers(int count, String server, long pingMillis) {
        for (int i = 0; i < count; i++) {
            addPlayer("player" + i, server, pingMillis);
        }
        return this;
    }

    public FakeProxyPlatform removeAllPlayers() {
        players.clear();
        return this;
    }

    public FakeProxyPlatform addServer(FakeBackendServer server) {
        servers.add(server);
        return this;
    }

    public FakeProxyPlatform removeServer(String name) {
        servers.removeIf(server -> server.name().equals(name));
        return this;
    }

    public FakeProxyPlatform addPlugin(String id, String version) {
        plugins.add(new PluginDescriptor(id, version));
        return this;
    }

    /** Backend handles as the concrete fake type, for assertions on ping behaviour. */
    public List<FakeBackendServer> fakeServers() {
        List<FakeBackendServer> result = new ArrayList<>();
        servers.forEach(server -> result.add((FakeBackendServer) server));
        return result;
    }

    public int playerCountCalls() {
        return playerCountCalls.get();
    }
}
