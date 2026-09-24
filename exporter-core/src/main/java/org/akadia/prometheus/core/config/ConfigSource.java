package org.akadia.prometheus.core.config;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves one dotted config path against the override sources, in precedence order:
 *
 * <ol>
 *   <li>environment variable, e.g. {@code MC_PROXY_EXPORTER_PORT}</li>
 *   <li>system property, e.g. {@code -Dmc.proxy.exporter.port=9985}</li>
 *   <li>the parsed YAML file</li>
 *   <li>the compiled-in default</li>
 * </ol>
 *
 * <p>Environment first is what makes the plugin usable in Docker and Kubernetes without
 * baking a config file into the image.
 */
public final class ConfigSource {

    public static final String ENV_PREFIX = "MC_PROXY_EXPORTER_";
    public static final String PROPERTY_PREFIX = "mc.proxy.exporter.";

    private final Map<String, String> environment;
    private final Map<String, String> systemProperties;
    private final Map<String, Object> yaml;

    ConfigSource(Map<String, String> environment,
                 Map<String, String> systemProperties,
                 Map<String, Object> yaml) {
        this.environment = environment;
        this.systemProperties = systemProperties;
        this.yaml = yaml;
    }

    /** Wires up the live JVM environment and system properties. */
    public static ConfigSource ofRuntime(Map<String, Object> yaml) {
        Map<String, String> props = new java.util.HashMap<>();
        System.getProperties().stringPropertyNames()
                .forEach(name -> props.put(name, System.getProperty(name)));
        return new ConfigSource(System.getenv(), props, yaml == null ? Map.of() : yaml);
    }

    /** Deterministic source for tests. */
    public static ConfigSource of(Map<String, String> environment,
                                  Map<String, String> systemProperties,
                                  Map<String, Object> yaml) {
        return new ConfigSource(
                environment == null ? Map.of() : environment,
                systemProperties == null ? Map.of() : systemProperties,
                yaml == null ? Map.of() : yaml);
    }

    /**
     * Looks up {@code path} (e.g. {@code "collection.backend-ping-interval-seconds"})
     * across all sources.
     */
    public Optional<String> find(String path) {
        String env = environment.get(envKey(path));
        if (isPresent(env)) {
            return Optional.of(env.trim());
        }
        String property = systemProperties.get(PROPERTY_PREFIX + path);
        if (isPresent(property)) {
            return Optional.of(property.trim());
        }
        return yamlValue(path).map(String::trim).filter(ConfigSource::isPresent);
    }

    /**
     * Looks up {@code path} without trimming the value.
     *
     * <p>{@link #find} trims, which is right for ports, hostnames and booleans where
     * stray whitespace is always accidental. It is wrong for a password: an operator who
     * quotes {@code "  hunter2  "} in YAML, or exports it with trailing whitespace, means
     * those spaces, and silently removing them turns into a 401 that looks like the
     * credentials are simply wrong.
     */
    public Optional<String> findSecret(String path) {
        String env = environment.get(envKey(path));
        if (env != null && !env.isEmpty()) {
            return Optional.of(env);
        }
        String property = systemProperties.get(PROPERTY_PREFIX + path);
        if (property != null && !property.isEmpty()) {
            return Optional.of(property);
        }
        return yamlValue(path).filter(value -> !value.isEmpty());
    }

    /** {@code collection.backend-ping-interval-seconds} to {@code MC_PROXY_EXPORTER_COLLECTION_BACKEND_PING_INTERVAL_SECONDS}. */
    public static String envKey(String path) {
        return ENV_PREFIX + path.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    /** Raw YAML node at {@code path}, for sections that are parsed as a whole. */
    public Optional<Object> yamlNode(String path) {
        Object current = yaml;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            current = map.get(segment);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    private Optional<String> yamlValue(String path) {
        return yamlNode(path)
                .filter(value -> !(value instanceof Map) && !(value instanceof Iterable))
                .map(String::valueOf);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
