package org.akadia.prometheus.velocity;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.akadia.prometheus.core.platform.BackendPing;
import org.akadia.prometheus.core.platform.BackendServerHandle;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/** One of Velocity's registered backend servers. */
final class VelocityBackendServer implements BackendServerHandle {

    private final RegisteredServer server;

    VelocityBackendServer(RegisteredServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return server.getServerInfo().getName();
    }

    @Override
    public String address() {
        InetSocketAddress address = server.getServerInfo().getAddress();
        if (address == null) {
            return "unknown";
        }
        return address.getHostString() + ":" + address.getPort();
    }

    @Override
    public int connectedPlayers() {
        return server.getPlayersConnected().size();
    }

    @Override
    public CompletableFuture<BackendPing> ping() {
        return server.ping().thenApply(VelocityBackendServer::toBackendPing);
    }

    private static BackendPing toBackendPing(ServerPing response) {
        if (response == null) {
            return BackendPing.empty();
        }

        int online = response.getPlayers().map(ServerPing.Players::getOnline)
                .orElse(BackendPing.UNKNOWN);
        int max = response.getPlayers().map(ServerPing.Players::getMax)
                .orElse(BackendPing.UNKNOWN);

        int protocol = BackendPing.UNKNOWN;
        String versionName = null;
        ServerPing.Version version = response.getVersion();
        if (version != null) {
            protocol = version.getProtocol();
            versionName = version.getName();
        }
        return new BackendPing(online, max, protocol, versionName);
    }
}
