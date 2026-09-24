package org.akadia.prometheus.core.platform;

/**
 * A plugin loaded by the proxy.
 *
 * @param id      plugin identifier, unique on the proxy
 * @param version plugin version, {@code "unknown"} when the proxy does not report one
 */
public record PluginDescriptor(String id, String version) {

    public PluginDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("plugin id must not be blank");
        }
        version = version == null || version.isBlank() ? "unknown" : version;
    }
}
