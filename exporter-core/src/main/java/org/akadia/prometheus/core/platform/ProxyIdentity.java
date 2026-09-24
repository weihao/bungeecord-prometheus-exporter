package org.akadia.prometheus.core.platform;

import java.util.Objects;

/**
 * Static identity of the running proxy, exported as {@code <namespace>_info}.
 *
 * @param type          platform discriminator, {@code "bungeecord"} or {@code "velocity"}
 * @param name          proxy brand name, e.g. {@code "Waterfall"} or {@code "Velocity"}
 * @param version       proxy version string
 * @param onlineMode    whether the proxy authenticates players against Mojang
 */
public record ProxyIdentity(String type, String name, String version, boolean onlineMode) {

    public static final String TYPE_BUNGEECORD = "bungeecord";
    public static final String TYPE_VELOCITY = "velocity";

    public ProxyIdentity {
        type = blankToUnknown(type);
        name = blankToUnknown(name);
        version = blankToUnknown(version);
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    @Override
    public String toString() {
        return Objects.toString(name) + " " + version + " (" + type + ")";
    }
}
