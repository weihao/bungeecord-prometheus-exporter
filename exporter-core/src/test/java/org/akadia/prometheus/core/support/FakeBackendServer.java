package org.akadia.prometheus.core.support;

import org.akadia.prometheus.core.platform.BackendPing;
import org.akadia.prometheus.core.platform.BackendServerHandle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A backend server whose ping behaviour is scripted.
 *
 * <p>Covers the four cases the real world produces: answers, refuses, hangs forever, and
 * blows up synchronously before returning a future at all.
 */
public final class FakeBackendServer implements BackendServerHandle {

    private enum Behaviour { RESPOND, FAIL, HANG, THROW_SYNC }

    private final String name;
    private final String address;
    private final AtomicInteger pingCount = new AtomicInteger();

    private volatile Behaviour behaviour = Behaviour.RESPOND;
    private volatile BackendPing response = new BackendPing(3, 20, 767, "1.21.4");
    private volatile int connectedPlayers;

    public FakeBackendServer(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public static FakeBackendServer named(String name) {
        return new FakeBackendServer(name, name + ".example:25565");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public int connectedPlayers() {
        return connectedPlayers;
    }

    @Override
    public CompletableFuture<BackendPing> ping() {
        pingCount.incrementAndGet();
        return switch (behaviour) {
            case RESPOND -> CompletableFuture.completedFuture(response);
            case FAIL -> CompletableFuture.failedFuture(
                    new java.net.ConnectException("connection refused"));
            // Never completes: the exporter's own timeout has to be what ends the wait.
            case HANG -> new CompletableFuture<>();
            case THROW_SYNC -> throw new IllegalStateException("server unregistered mid-ping");
        };
    }

    // -- test controls ----------------------------------------------------------------

    public FakeBackendServer responding(BackendPing response) {
        this.response = response;
        this.behaviour = Behaviour.RESPOND;
        return this;
    }

    public FakeBackendServer failing() {
        this.behaviour = Behaviour.FAIL;
        return this;
    }

    public FakeBackendServer hanging() {
        this.behaviour = Behaviour.HANG;
        return this;
    }

    public FakeBackendServer throwingSynchronously() {
        this.behaviour = Behaviour.THROW_SYNC;
        return this;
    }

    public FakeBackendServer connectedPlayers(int connectedPlayers) {
        this.connectedPlayers = connectedPlayers;
        return this;
    }

    public int pingCount() {
        return pingCount.get();
    }
}
