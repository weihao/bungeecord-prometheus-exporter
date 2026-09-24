package org.akadia.prometheus.core.platform;

/**
 * Result of a server-list ping against a backend server.
 *
 * @param onlinePlayers   players reported by the backend, or -1 if it did not say
 * @param maxPlayers      slot limit reported by the backend, or -1 if it did not say
 * @param protocolVersion Minecraft protocol number, or -1 if unknown
 * @param versionName     human-readable version, e.g. {@code "1.21.4"}
 */
public record BackendPing(int onlinePlayers, int maxPlayers, int protocolVersion, String versionName) {

    public static final int UNKNOWN = -1;

    public BackendPing {
        versionName = versionName == null || versionName.isBlank() ? "unknown" : versionName;
    }

    public static BackendPing empty() {
        return new BackendPing(UNKNOWN, UNKNOWN, UNKNOWN, "unknown");
    }
}
