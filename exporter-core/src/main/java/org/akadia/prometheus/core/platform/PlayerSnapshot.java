package org.akadia.prometheus.core.platform;

import java.util.UUID;

/**
 * Immutable view of one connected player.
 *
 * @param uuid       player UUID, never {@code null}
 * @param username   player name, never {@code null}
 * @param serverName backend server the player is on, or {@code null} while connecting
 * @param pingMillis round-trip latency in milliseconds, or a negative value if unknown
 */
public record PlayerSnapshot(UUID uuid, String username, String serverName, long pingMillis) {

    /** Label value used when a player is not attached to any backend server. */
    public static final String NO_SERVER = "none";

    public PlayerSnapshot {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid must not be null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    /** Backend server name, or {@link #NO_SERVER} when the player is not on one. */
    public String serverNameOrNone() {
        return serverName == null || serverName.isBlank() ? NO_SERVER : serverName;
    }

    public boolean hasKnownPing() {
        return pingMillis >= 0;
    }

    /** Latency in seconds, the Prometheus base unit. */
    public double pingSeconds() {
        return pingMillis / 1000.0d;
    }
}
