package org.akadia.prometheus.bungeecord;

import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;
import org.akadia.prometheus.core.platform.BackendPing;
import org.akadia.prometheus.core.platform.BackendServerHandle;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/** One entry from BungeeCord's {@code servers} map. */
final class BungeeCordBackendServer implements BackendServerHandle {

    private final ServerInfo info;

    BungeeCordBackendServer(ServerInfo info) {
        this.info = info;
    }

    @Override
    public String name() {
        return info.getName();
    }

    @Override
    public String address() {
        InetSocketAddress address = info.getAddress();
        if (address == null) {
            return "unknown";
        }
        return address.getHostString() + ":" + address.getPort();
    }

    @Override
    public int connectedPlayers() {
        return info.getPlayers().size();
    }

    @Override
    public CompletableFuture<BackendPing> ping() {
        CompletableFuture<BackendPing> future = new CompletableFuture<>();
        // BungeeCord's ping is callback-based and fires on a Netty thread. Adapting it to a
        // future lets the core time it out uniformly with Velocity's.
        info.ping((result, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else if (result == null) {
                future.completeExceptionally(new IllegalStateException("empty ping response"));
            } else {
                future.complete(toBackendPing(result));
            }
        });
        return future;
    }

    private static BackendPing toBackendPing(ServerPing response) {
        int online = BackendPing.UNKNOWN;
        int max = BackendPing.UNKNOWN;
        ServerPing.Players players = response.getPlayers();
        if (players != null) {
            online = players.getOnline();
            max = players.getMax();
        }

        int protocol = BackendPing.UNKNOWN;
        String versionName = null;
        ServerPing.Protocol version = response.getVersion();
        if (version != null) {
            protocol = version.getProtocol();
            versionName = version.getName();
        }
        return new BackendPing(online, max, protocol, versionName);
    }
}
