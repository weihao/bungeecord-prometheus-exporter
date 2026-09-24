package org.akadia.prometheus.core.platform;

/**
 * Network-wide counts from a multi-proxy coordinator such as RedisBungee.
 *
 * <p>Optional: a single-proxy deployment has no such thing, and the corresponding metrics
 * are simply not registered. Implementations are called from the scrape thread and must
 * not block on the network.
 */
public interface NetworkStatsProvider {

    /**
     * Whether the coordinator can be queried right now.
     *
     * <p>Not a constant: a coordinator plugin may enable after this one, so a provider is
     * allowed to start unavailable and become available later. Collectors emit nothing
     * while this is false, which produces a gap rather than a misleading zero.
     */
    default boolean isAvailable() {
        return true;
    }

    /** Players connected across every proxy in the network. */
    int networkPlayerCount();

    /** Proxies currently participating in the network, including this one. */
    int proxyCount();
}
