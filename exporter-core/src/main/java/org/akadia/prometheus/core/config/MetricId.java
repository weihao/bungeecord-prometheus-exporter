package org.akadia.prometheus.core.config;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Toggleable metric groups, keyed by the {@code metrics:} section of {@code config.yml}.
 *
 * <p>Groups whose cardinality scales with the player base default to off. A gauge with a
 * {@code player} label produces one time series per player who has ever connected during
 * the retention window, which is exactly the shape that overwhelms a Prometheus server.
 */
public enum MetricId {

    /** Standard {@code jvm_*} and {@code process_*} metrics from the Prometheus JVM instrumentation. */
    JVM("jvm", true, false),

    /** {@code _info}, {@code _start_time_seconds} and {@code _uptime_seconds}. */
    PROXY_INFO("proxy-info", true, false),

    /** Total players connected to the proxy. */
    PLAYERS_ONLINE("players-online", true, false),

    /** Players per backend server. One series per server. */
    PLAYERS_PER_SERVER("players-per-server", true, false),

    /** Latency distribution across all players, sampled on an interval. */
    PLAYER_PING_HISTOGRAM("player-ping-histogram", true, false),

    /** One latency gauge per connected player. */
    PLAYER_PING_PER_PLAYER("player-ping-per-player", false, "one series per player"),

    /** One presence gauge per connected player. */
    PLAYER_LIST("player-list", false, "one series per player"),

    /** Registered backend server count and per-server info. */
    SERVERS("servers", true, false),

    /** Reachability and ping latency of each backend, sampled in the background. */
    BACKEND_HEALTH("backend-health", true, false),

    /** Installed plugin count and per-plugin info. */
    PLUGINS("plugins", true, false),

    /** Event counters: logins, disconnects, kicks, chat, commands, server switches, list pings. */
    EVENTS("events", true, false),

    /** Adds a {@code command} label to the command counter. */
    COMMAND_LABEL("command-label", false, "one series per distinct command, and commands are player input"),

    /** Network-wide player and proxy counts from RedisBungee. */
    REDISBUNGEE("redisbungee", false, null);

    private final String key;
    private final boolean enabledByDefault;
    private final String cardinalityNote;

    MetricId(String key, boolean enabledByDefault, boolean highCardinality) {
        this(key, enabledByDefault, highCardinality ? "one series per player" : null);
    }

    MetricId(String key, boolean enabledByDefault, String cardinalityNote) {
        this.key = key;
        this.enabledByDefault = enabledByDefault;
        this.cardinalityNote = cardinalityNote;
    }

    /** Key used in {@code config.yml} under {@code metrics:}. */
    public String key() {
        return key;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }

    /** Whether the series count of this group grows without a fixed bound. */
    public boolean highCardinality() {
        return cardinalityNote != null;
    }

    /** How this group's cardinality grows, or {@code null} when it is bounded. */
    public String cardinalityNote() {
        return cardinalityNote;
    }

    public static Optional<MetricId> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalised = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays.stream(values()).filter(id -> id.key.equals(normalised)).findFirst();
    }

    public static Set<MetricId> defaults() {
        EnumSet<MetricId> defaults = EnumSet.noneOf(MetricId.class);
        for (MetricId id : values()) {
            if (id.enabledByDefault) {
                defaults.add(id);
            }
        }
        return defaults;
    }
}
