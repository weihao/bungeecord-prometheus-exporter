package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.model.snapshots.Unit;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.PlayerSnapshot;
import org.akadia.prometheus.core.platform.ProxyPlatform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is online, and where.
 *
 * <p>Three levels of detail, because they have three very different costs:
 *
 * <ul>
 *   <li>{@code players_online} - one series, always on</li>
 *   <li>{@code server_players_online} - one series per backend, always on</li>
 *   <li>{@code player_online} / {@code player_ping_seconds} - one series per player,
 *       off by default and bounded by {@link CardinalityGuard} when enabled</li>
 * </ul>
 *
 * <p>The per-server gauge is derived from the player list rather than from each backend's
 * own counter so that the numbers always add up to {@code players_online}; the two proxy
 * APIs disagree transiently while a player is switching servers.
 */
public final class PlayerMetrics {

    private final ProxyPlatform platform;

    public PlayerMetrics(ExporterConfig config,
                         ProxyPlatform platform,
                         CardinalityGuard guard,
                         MetricRegistrar registrar) {
        this.platform = platform;

        if (config.isEnabled(MetricId.PLAYERS_ONLINE)) {
            registrar.register(MetricNames.PLAYERS_ONLINE, GaugeWithCallback.builder()
                    .name(config.metricName(MetricNames.PLAYERS_ONLINE))
                    .help("Players currently connected to the proxy")
                    .callback(callback -> callback.call(platform.onlinePlayerCount()))
                    .build());
        }

        if (config.isEnabled(MetricId.PLAYERS_PER_SERVER)) {
            String metric = config.metricName(MetricNames.SERVER_PLAYERS_ONLINE);
            registrar.register(MetricNames.SERVER_PLAYERS_ONLINE, GaugeWithCallback.builder()
                    .name(metric)
                    .help("Players connected to the proxy, by backend server")
                    .labelNames(MetricNames.LABEL_SERVER)
                    .callback(callback -> playersByServer().forEach((server, count) -> {
                        if (guard.admit(metric, server)) {
                            callback.call(count, server);
                        }
                    }))
                    .build());
        }

        if (config.isEnabled(MetricId.PLAYER_LIST)) {
            String metric = config.metricName(MetricNames.PLAYER_ONLINE);
            registrar.register(MetricNames.PLAYER_ONLINE, GaugeWithCallback.builder()
                    .name(metric)
                    .help("Always 1 for each connected player. High cardinality: one series per player")
                    .labelNames(MetricNames.LABEL_PLAYER, MetricNames.LABEL_UUID, MetricNames.LABEL_SERVER)
                    .callback(callback -> {
                        for (PlayerSnapshot player : platform.players()) {
                            String uuid = player.uuid().toString();
                            String server = player.serverNameOrNone();
                            if (guard.admit(metric, player.username(), uuid, server)) {
                                callback.call(1, player.username(), uuid, server);
                            }
                        }
                    })
                    .build());
        }

        if (config.isEnabled(MetricId.PLAYER_PING_PER_PLAYER)) {
            String metric = config.metricName(MetricNames.PLAYER_LATENCY_SECONDS);
            registrar.register(MetricNames.PLAYER_LATENCY_SECONDS, GaugeWithCallback.builder()
                    .name(metric)
                    .unit(Unit.SECONDS)
                    .help("Round-trip latency of each connected player. High cardinality: "
                            + "one series per player")
                    .labelNames(MetricNames.LABEL_PLAYER, MetricNames.LABEL_UUID, MetricNames.LABEL_SERVER)
                    .callback(callback -> {
                        for (PlayerSnapshot player : platform.players()) {
                            if (!player.hasKnownPing()) {
                                continue;
                            }
                            String uuid = player.uuid().toString();
                            String server = player.serverNameOrNone();
                            if (guard.admit(metric, player.username(), uuid, server)) {
                                callback.call(player.pingSeconds(), player.username(), uuid, server);
                            }
                        }
                    })
                    .build());
        }
    }

    /**
     * Players grouped by backend server.
     *
     * <p>Every registered server is seeded with zero so an emptied server reports 0 rather
     * than vanishing from the output - a disappearing series looks like a broken scrape in
     * Grafana, while an explicit zero graphs correctly.
     */
    Map<String, Long> playersByServer() {
        Map<String, Long> counts = new LinkedHashMap<>();
        platform.servers().forEach(server -> counts.put(server.name(), 0L));
        List<PlayerSnapshot> players = platform.players();
        for (PlayerSnapshot player : players) {
            counts.merge(player.serverNameOrNone(), 1L, Long::sum);
        }
        return counts;
    }
}
