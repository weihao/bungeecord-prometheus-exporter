package org.akadia.prometheus.core.platform;

import java.util.List;

/**
 * Everything the exporter needs to know about the proxy it runs on.
 *
 * <p>This is the only seam between the metric pipeline and BungeeCord/Velocity. The core
 * module has no compile dependency on either proxy, so every collector can be exercised
 * against {@code FakeProxyPlatform} in a plain JVM test.
 *
 * <p><strong>Threading:</strong> every method here is called from the HTTP scrape thread
 * and from the background sampler thread, so implementations must be safe to call
 * concurrently and must not block. Anything that needs network I/O belongs on
 * {@link BackendServerHandle#ping()}, which is explicitly asynchronous.
 */
public interface ProxyPlatform {

    /** Static description of the proxy, read once at startup. */
    ProxyIdentity identity();

    /**
     * Number of players connected to the proxy.
     *
     * <p>Separate from {@code players().size()} because both proxies expose a cheap
     * counter, and the scrape path should not materialise a player list just to count.
     */
    int onlinePlayerCount();

    /** Point-in-time view of the connected players. Never {@code null}. */
    List<PlayerSnapshot> players();

    /** Backend servers registered with the proxy. Never {@code null}. */
    List<BackendServerHandle> servers();

    /** Plugins loaded by the proxy, including this exporter. Never {@code null}. */
    List<PluginDescriptor> plugins();

    /** Bridge to the proxy's logger. */
    PlatformLogger logger();
}
