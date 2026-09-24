package org.akadia.prometheus.core.platform;

import java.util.concurrent.CompletableFuture;

/** A backend Minecraft server registered with the proxy. */
public interface BackendServerHandle {

    /** Server name as configured on the proxy, used as the {@code server} label. */
    String name();

    /** {@code host:port} of the backend, used as a label on the info metric. */
    String address();

    /** Players currently routed to this backend. Must not block. */
    int connectedPlayers();

    /**
     * Server-list-pings the backend.
     *
     * <p>Called only from the background sampler, never from the scrape path, so the
     * returned future may take as long as the network does. Implementations must
     * complete the future exceptionally rather than throwing, and must never return
     * {@code null}.
     */
    CompletableFuture<BackendPing> ping();
}
