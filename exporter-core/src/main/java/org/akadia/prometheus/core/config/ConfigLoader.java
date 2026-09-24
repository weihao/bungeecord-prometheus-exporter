package org.akadia.prometheus.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Turns {@code config.yml} plus environment overrides into a validated {@link ExporterConfig}. */
public final class ConfigLoader {

    public static final String CONFIG_FILE_NAME = "config.yml";
    private static final String DEFAULT_CONFIG_RESOURCE = "config.yml";

    private ConfigLoader() {
    }

    /**
     * Loads configuration from {@code dataDirectory/config.yml}, writing the bundled default
     * file first if it is not there yet.
     *
     * @param warnings receives non-fatal problems, e.g. unknown keys, so the caller can log
     *                 them through the proxy's logger
     */
    public static ExporterConfig load(Path dataDirectory, Consumer<String> warnings) {
        Map<String, Object> yaml = readOrCreate(dataDirectory, warnings);
        return from(ConfigSource.ofRuntime(yaml), warnings);
    }

    /** Builds a configuration from an already-resolved source. The unit-test entry point. */
    public static ExporterConfig from(ConfigSource source, Consumer<String> warnings) {
        ExporterConfig.Builder builder = ExporterConfig.builder();

        source.find("host").ifPresent(builder::host);
        source.find("port").ifPresent(value -> builder.port(parseInt(value, "port")));
        source.find("path").ifPresent(builder::path);
        source.find("namespace").ifPresent(builder::namespace);

        source.find("auth.enabled").ifPresent(value -> builder.authEnabled(parseBoolean(value, "auth.enabled")));
        source.find("auth.username").ifPresent(builder::authUsername);
        // Deliberately not source.find(): that trims, and a password's whitespace is
        // part of the password.
        source.findSecret("auth.password").ifPresent(builder::authPassword);

        source.find("collection.backend-ping-interval-seconds")
                .ifPresent(value -> builder.backendPingInterval(parseSeconds(value, "collection.backend-ping-interval-seconds")));
        source.find("collection.backend-ping-timeout-seconds")
                .ifPresent(value -> builder.backendPingTimeout(parseSeconds(value, "collection.backend-ping-timeout-seconds")));
        source.find("collection.player-ping-sample-interval-seconds")
                .ifPresent(value -> builder.playerPingSampleInterval(parseSeconds(value, "collection.player-ping-sample-interval-seconds")));
        source.find("collection.max-series-per-metric")
                .ifPresent(value -> builder.maxSeriesPerMetric(parseInt(value, "collection.max-series-per-metric")));

        source.find("bstats").ifPresent(value -> builder.bstats(parseBoolean(value, "bstats")));

        builder.enabledMetrics(resolveMetrics(source, warnings));

        return builder.build();
    }

    private static Set<MetricId> resolveMetrics(ConfigSource source, Consumer<String> warnings) {
        Set<MetricId> enabled = EnumSet.noneOf(MetricId.class);
        for (MetricId id : MetricId.values()) {
            boolean value = findMetricToggle(source, id)
                    .map(raw -> parseBoolean(raw, "metrics." + id.key()))
                    .orElse(id.enabledByDefault());
            if (value) {
                enabled.add(id);
                if (id.highCardinality()) {
                    warnings.accept("metrics." + id.key() + " is enabled; it emits "
                            + id.cardinalityNote()
                            + " and can overwhelm Prometheus on a busy network");
                }
            }
        }

        // Surface typos instead of silently ignoring them.
        source.yamlNode("metrics").ifPresent(node -> {
            if (node instanceof Map<?, ?> map) {
                map.keySet().stream()
                        .map(String::valueOf)
                        .filter(key -> MetricId.fromKey(key).isEmpty())
                        .forEach(key -> warnings.accept("unknown key metrics." + key + " in "
                                + CONFIG_FILE_NAME + "; known keys: " + knownMetricKeys()));
            }
        });

        return enabled;
    }

    /**
     * Finds a metric toggle, accepting both spellings of the key.
     *
     * <p>The documented key uses dashes ({@code player-list}), but underscores are the
     * more common convention in Minecraft plugin configs and the environment-variable form
     * uses them too, so {@code player_list} has to resolve to the same setting. Without
     * this, {@link MetricId#fromKey} would accept the underscore spelling well enough to
     * suppress the unknown-key warning while the value was silently ignored.
     */
    private static Optional<String> findMetricToggle(ConfigSource source, MetricId id) {
        Optional<String> dashed = source.find("metrics." + id.key());
        if (dashed.isPresent()) {
            return dashed;
        }
        String underscored = id.key().replace('-', '_');
        return underscored.equals(id.key())
                ? Optional.empty()
                : source.find("metrics." + underscored);
    }

    private static String knownMetricKeys() {
        return String.join(", ", java.util.Arrays.stream(MetricId.values()).map(MetricId::key).toList());
    }

    private static Map<String, Object> readOrCreate(Path dataDirectory, Consumer<String> warnings) {
        Path configFile = dataDirectory.resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(dataDirectory);
            if (!Files.exists(configFile)) {
                writeDefaultConfig(configFile);
            }
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                return parse(reader);
            }
        } catch (IOException e) {
            warnings.accept("could not read " + configFile + " (" + e.getMessage()
                    + "); continuing with defaults and environment overrides");
            return Map.of();
        }
    }

    private static void writeDefaultConfig(Path configFile) throws IOException {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (in == null) {
                throw new IOException("bundled " + DEFAULT_CONFIG_RESOURCE + " is missing from the jar");
            }
            Files.copy(in, configFile);
        }
    }

    /** Parses YAML into a nested map. Visible for testing. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(Reader reader) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        // A config file is never big; refuse anything that looks like a decompression bomb.
        options.setCodePointLimit(1024 * 1024);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded = yaml.load(reader);
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map)) {
            throw new ConfigurationException(CONFIG_FILE_NAME + " must contain a YAML mapping at the top level");
        }
        return (Map<String, Object>) loaded;
    }

    private static int parseInt(String value, String path) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException(path + " must be a whole number, got '" + value + "'");
        }
    }

    private static Duration parseSeconds(String value, String path) {
        try {
            double seconds = Double.parseDouble(value.trim());
            if (seconds < 0) {
                throw new ConfigurationException(path + " must not be negative, got " + value);
            }
            return Duration.ofMillis(Math.round(seconds * 1000));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(path + " must be a number of seconds, got '" + value + "'");
        }
    }

    private static final List<String> TRUE_VALUES = List.of("true", "yes", "on", "1", "enabled");
    private static final List<String> FALSE_VALUES = List.of("false", "no", "off", "0", "disabled");

    private static boolean parseBoolean(String value, String path) {
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalised)) {
            return true;
        }
        if (FALSE_VALUES.contains(normalised)) {
            return false;
        }
        throw new ConfigurationException(path + " must be true or false, got '" + value + "'");
    }
}
