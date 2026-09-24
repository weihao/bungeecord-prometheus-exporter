package org.akadia.prometheus.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.ProxyVersion;
import org.akadia.prometheus.core.platform.BackendServerHandle;
import org.akadia.prometheus.core.platform.PlatformLogger;
import org.akadia.prometheus.core.platform.PlayerSnapshot;
import org.akadia.prometheus.core.platform.PluginDescriptor;
import org.akadia.prometheus.core.platform.ProxyIdentity;
import org.akadia.prometheus.core.platform.ProxyPlatform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Maps the Velocity API onto {@link ProxyPlatform}. */
final class VelocityPlatform implements ProxyPlatform {

    private final ProxyServer proxy;
    private final PlatformLogger logger;
    private final ProxyIdentity identity;

    VelocityPlatform(ProxyServer proxy, PlatformLogger logger) {
        this.proxy = proxy;
        this.logger = logger;
        ProxyVersion version = proxy.getVersion();
        this.identity = new ProxyIdentity(
                ProxyIdentity.TYPE_VELOCITY,
                version.getName(),
                version.getVersion(),
                proxy.getConfiguration().isOnlineMode());
    }

    @Override
    public ProxyIdentity identity() {
        return identity;
    }

    @Override
    public int onlinePlayerCount() {
        return proxy.getPlayerCount();
    }

    @Override
    public List<PlayerSnapshot> players() {
        Collection<Player> connected = proxy.getAllPlayers();
        List<PlayerSnapshot> snapshots = new ArrayList<>(connected.size());
        for (Player player : connected) {
            if (player == null || player.getUniqueId() == null || player.getUsername() == null) {
                continue;
            }
            snapshots.add(new PlayerSnapshot(
                    player.getUniqueId(),
                    player.getUsername(),
                    currentServerName(player),
                    player.getPing()));
        }
        return snapshots;
    }

    @Override
    public List<BackendServerHandle> servers() {
        Collection<RegisteredServer> registered = proxy.getAllServers();
        List<BackendServerHandle> handles = new ArrayList<>(registered.size());
        for (RegisteredServer server : registered) {
            if (server != null) {
                handles.add(new VelocityBackendServer(server));
            }
        }
        return handles;
    }

    @Override
    public List<PluginDescriptor> plugins() {
        Collection<PluginContainer> containers = proxy.getPluginManager().getPlugins();
        List<PluginDescriptor> descriptors = new ArrayList<>(containers.size());
        for (PluginContainer container : containers) {
            if (container == null || container.getDescription() == null) {
                continue;
            }
            PluginDescription description = container.getDescription();
            descriptors.add(new PluginDescriptor(
                    description.getId(),
                    description.getVersion().orElse(null)));
        }
        return descriptors;
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    private static String currentServerName(Player player) {
        // Empty between the login completing and the initial server connection.
        Optional<ServerConnection> connection = player.getCurrentServer();
        return connection.map(server -> server.getServerInfo().getName()).orElse(null);
    }
}
