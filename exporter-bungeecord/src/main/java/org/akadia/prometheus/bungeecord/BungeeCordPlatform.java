package org.akadia.prometheus.bungeecord;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.core.platform.BackendServerHandle;
import org.akadia.prometheus.core.platform.PlatformLogger;
import org.akadia.prometheus.core.platform.PlayerSnapshot;
import org.akadia.prometheus.core.platform.PluginDescriptor;
import org.akadia.prometheus.core.platform.ProxyIdentity;
import org.akadia.prometheus.core.platform.ProxyPlatform;

import java.util.ArrayList;
import java.util.List;

/** Maps the BungeeCord / Waterfall API onto {@link ProxyPlatform}. */
final class BungeeCordPlatform implements ProxyPlatform {

    private final ProxyServer proxy;
    private final PlatformLogger logger;
    private final ProxyIdentity identity;

    BungeeCordPlatform(ProxyServer proxy, PlatformLogger logger) {
        this.proxy = proxy;
        this.logger = logger;
        this.identity = new ProxyIdentity(
                ProxyIdentity.TYPE_BUNGEECORD,
                proxy.getName(),
                proxy.getVersion(),
                proxy.getConfig().isOnlineMode());
    }

    @Override
    public ProxyIdentity identity() {
        return identity;
    }

    @Override
    public int onlinePlayerCount() {
        return proxy.getOnlineCount();
    }

    @Override
    public List<PlayerSnapshot> players() {
        java.util.Collection<ProxiedPlayer> connected = proxy.getPlayers();
        List<PlayerSnapshot> snapshots = new ArrayList<>(connected.size());
        for (ProxiedPlayer player : connected) {
            // The collection is live: a player can disconnect between the iterator handing
            // it over and us reading it, at which point these accessors return null.
            if (player == null || player.getUniqueId() == null || player.getName() == null) {
                continue;
            }
            snapshots.add(new PlayerSnapshot(
                    player.getUniqueId(),
                    player.getName(),
                    currentServerName(player),
                    player.getPing()));
        }
        return snapshots;
    }

    @Override
    public List<BackendServerHandle> servers() {
        java.util.Collection<ServerInfo> infos = proxy.getServers().values();
        List<BackendServerHandle> handles = new ArrayList<>(infos.size());
        for (ServerInfo info : infos) {
            if (info != null) {
                handles.add(new BungeeCordBackendServer(info));
            }
        }
        return handles;
    }

    @Override
    public List<PluginDescriptor> plugins() {
        java.util.Collection<Plugin> loaded = proxy.getPluginManager().getPlugins();
        List<PluginDescriptor> descriptors = new ArrayList<>(loaded.size());
        for (Plugin plugin : loaded) {
            if (plugin == null || plugin.getDescription() == null
                    || plugin.getDescription().getName() == null) {
                continue;
            }
            descriptors.add(new PluginDescriptor(
                    plugin.getDescription().getName(),
                    plugin.getDescription().getVersion()));
        }
        return descriptors;
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    private static String currentServerName(ProxiedPlayer player) {
        Server server = player.getServer();
        if (server == null || server.getInfo() == null) {
            // Normal during the handshake window, before the player is routed anywhere.
            return null;
        }
        return server.getInfo().getName();
    }
}
