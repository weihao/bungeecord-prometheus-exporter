package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.model.snapshots.Unit;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.BackendPing;
import org.akadia.prometheus.core.platform.BackendServerHandle;
import org.akadia.prometheus.core.platform.ProxyPlatform;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Reachability of each backend server, measured by server-list-pinging it on an interval.
 *
 * <p>This is the metric the old exporter was missing and the one an operator actually
 * pages on: {@code minecraft_proxy_server_up == 0} means players are about to hit "Unable
 * to connect to server", and it fires before the player-count graph reacts.
 *
 * <p>Pings run on the sampler thread and results are cached, so a hung backend costs one
 * stale sample rather than a timed-out scrape. The cache is keyed on server name and
 * rebuilt from the live server list on every refresh, so unregistered backends stop being
 * reported instead of lingering forever.
 */
public final class BackendHealthMetrics {

    /** Sampler name used in logs and {@code exporter_sampler_runs_total}. */
    public static final String SAMPLER_NAME = "backend-ping";

    private final ProxyPlatform platform;
    private final Duration timeout;
    private final CardinalityGuard guard;
    private final Map<String, BackendState> state = new ConcurrentHashMap<>();

    private final String upMetric;

    public BackendHealthMetrics(ExporterConfig config,
                                ProxyPlatform platform,
                                CardinalityGuard guard,
                                MetricRegistrar registrar) {
        this.platform = platform;
        this.timeout = config.backendPingTimeout();
        this.guard = guard;
        this.upMetric = config.metricName(MetricNames.SERVER_UP);

        if (!config.isEnabled(MetricId.BACKEND_HEALTH)) {
            return;
        }

        registrar.register(MetricNames.SERVER_UP, GaugeWithCallback.builder()
                .name(upMetric)
                .help("1 if the backend server answered its last server-list ping, 0 if it did not")
                .labelNames(MetricNames.LABEL_SERVER)
                .callback(callback -> forEachState((server, snapshot) ->
                        callback.call(snapshot.up() ? 1 : 0, server)))
                .build());

        registrar.register(MetricNames.SERVER_PING_SECONDS, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.SERVER_PING_SECONDS))
                .unit(Unit.SECONDS)
                .help("Round-trip time of the last successful server-list ping to the backend")
                .labelNames(MetricNames.LABEL_SERVER)
                .callback(callback -> forEachState((server, snapshot) -> {
                    if (snapshot.up()) {
                        callback.call(snapshot.pingSeconds(), server);
                    }
                }))
                .build());

        registrar.register(MetricNames.SERVER_REPORTED_PLAYERS, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.SERVER_REPORTED_PLAYERS))
                .help("Player count the backend reported in its last ping response. May differ "
                        + "from the proxy's own count if players connect to the backend directly")
                .labelNames(MetricNames.LABEL_SERVER)
                .callback(callback -> forEachState((server, snapshot) -> {
                    if (snapshot.onlinePlayers() >= 0) {
                        callback.call(snapshot.onlinePlayers(), server);
                    }
                }))
                .build());

        registrar.register(MetricNames.SERVER_REPORTED_MAX_PLAYERS, GaugeWithCallback.builder()
                .name(config.metricName(MetricNames.SERVER_REPORTED_MAX_PLAYERS))
                .help("Slot limit the backend reported in its last ping response")
                .labelNames(MetricNames.LABEL_SERVER)
                .callback(callback -> forEachState((server, snapshot) -> {
                    if (snapshot.maxPlayers() >= 0) {
                        callback.call(snapshot.maxPlayers(), server);
                    }
                }))
                .build());
    }

    /**
     * Pings every backend once and replaces the cached state.
     *
     * <p>Blocks until all pings finish or time out, which is why it only ever runs on the
     * sampler thread. Pings are issued concurrently, so the call takes as long as the
     * slowest backend rather than the sum of all of them.
     */
    public void refresh() {
        List<BackendServerHandle> servers = platform.servers();

        List<CompletableFuture<Void>> pending = new ArrayList<>(servers.size());
        List<String> live = new ArrayList<>(servers.size());

        for (BackendServerHandle server : servers) {
            String name = server.name();
            live.add(name);
            long startNanos = System.nanoTime();
            pending.add(safePing(server)
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .handle((ping, error) -> {
                        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0d;
                        if (error != null || ping == null) {
                            state.put(name, BackendState.down());
                        } else {
                            state.put(name, new BackendState(true, seconds,
                                    ping.onlinePlayers(), ping.maxPlayers()));
                        }
                        return null;
                    }));
        }

        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();

        // Drop backends the proxy no longer knows about.
        state.keySet().retainAll(live);
    }

    private static CompletableFuture<BackendPing> safePing(BackendServerHandle server) {
        try {
            CompletableFuture<BackendPing> future = server.ping();
            return future == null ? CompletableFuture.failedFuture(
                    new IllegalStateException("ping() returned null")) : future;
        } catch (RuntimeException e) {
            // A proxy that throws synchronously must not abort the whole refresh.
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Iterates the cached state, subject to one shared cardinality budget.
     *
     * <p>All four backend metrics deliberately charge the same budget key: they carry the
     * identical {@code server} label set, so one budget for the server dimension is what
     * an operator means by the limit. The side effect is that drops are all attributed to
     * {@code server_up} in {@code exporter_series_dropped_total}.
     */
    private void forEachState(StateConsumer consumer) {
        state.forEach((server, snapshot) -> {
            if (guard.admit(upMetric, server)) {
                consumer.accept(server, snapshot);
            }
        });
    }

    @FunctionalInterface
    private interface StateConsumer {
        void accept(String server, BackendState state);
    }

    /**
     * Last known state of one backend.
     *
     * @param pingSeconds round-trip time of the successful ping, meaningless when down
     */
    record BackendState(boolean up, double pingSeconds, int onlinePlayers, int maxPlayers) {

        static BackendState down() {
            return new BackendState(false, 0, BackendPing.UNKNOWN, BackendPing.UNKNOWN);
        }
    }
}
