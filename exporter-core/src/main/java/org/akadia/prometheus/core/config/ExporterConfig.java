package org.akadia.prometheus.core.config;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validated, immutable exporter configuration.
 *
 * <p>Build with {@link #builder()}; {@link ConfigLoader} is what turns a YAML file plus
 * environment overrides into one of these.
 */
public final class ExporterConfig {

    /**
     * Prometheus metric names must match {@code [a-zA-Z_:][a-zA-Z0-9_:]*}. A namespace is
     * a name prefix, so it has to satisfy the same rule minus the trailing separator.
     * Colons are reserved for recording rules and are rejected here on purpose.
     */
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final String host;
    private final int port;
    private final String path;
    private final String namespace;
    private final boolean authEnabled;
    private final String authUsername;
    private final String authPassword;
    private final Duration backendPingInterval;
    private final Duration backendPingTimeout;
    private final Duration playerPingSampleInterval;
    private final int maxSeriesPerMetric;
    private final Set<MetricId> enabledMetrics;
    private final boolean bstats;

    private ExporterConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.path = builder.path;
        this.namespace = builder.namespace;
        this.authEnabled = builder.authEnabled;
        this.authUsername = builder.authUsername;
        this.authPassword = builder.authPassword;
        this.backendPingInterval = builder.backendPingInterval;
        this.backendPingTimeout = builder.backendPingTimeout;
        this.playerPingSampleInterval = builder.playerPingSampleInterval;
        this.maxSeriesPerMetric = builder.maxSeriesPerMetric;
        this.enabledMetrics = Collections.unmodifiableSet(EnumSet.copyOf(builder.enabledMetrics));
        this.bstats = builder.bstats;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Address the metrics endpoint binds to. {@code 0.0.0.0} for containers. */
    public String host() {
        return host;
    }

    /** Configured port, or 0 to let the OS pick one. */
    public int port() {
        return port;
    }

    /** HTTP path the metrics are served on, always starting with {@code /}. */
    public String path() {
        return path;
    }

    /** Metric name prefix, without the trailing underscore. */
    public String namespace() {
        return namespace;
    }

    public boolean authEnabled() {
        return authEnabled;
    }

    public String authUsername() {
        return authUsername;
    }

    public String authPassword() {
        return authPassword;
    }

    /** How often backends are pinged. {@link Duration#ZERO} disables the sampler. */
    public Duration backendPingInterval() {
        return backendPingInterval;
    }

    /** Per-backend ping deadline; a backend that misses it is reported as down. */
    public Duration backendPingTimeout() {
        return backendPingTimeout;
    }

    /** How often player latency is observed into the histogram. {@link Duration#ZERO} disables it. */
    public Duration playerPingSampleInterval() {
        return playerPingSampleInterval;
    }

    /** Hard cap on label combinations per metric, protecting the Prometheus server. */
    public int maxSeriesPerMetric() {
        return maxSeriesPerMetric;
    }

    public Set<MetricId> enabledMetrics() {
        return enabledMetrics;
    }

    public boolean isEnabled(MetricId id) {
        return enabledMetrics.contains(id);
    }

    public boolean bstats() {
        return bstats;
    }

    /** Prefixes a metric name with the configured namespace. */
    public String metricName(String suffix) {
        return namespace + "_" + suffix;
    }

    public Builder toBuilder() {
        return new Builder()
                .host(host)
                .port(port)
                .path(path)
                .namespace(namespace)
                .authEnabled(authEnabled)
                .authUsername(authUsername)
                .authPassword(authPassword)
                .backendPingInterval(backendPingInterval)
                .backendPingTimeout(backendPingTimeout)
                .playerPingSampleInterval(playerPingSampleInterval)
                .maxSeriesPerMetric(maxSeriesPerMetric)
                .enabledMetrics(enabledMetrics)
                .bstats(bstats);
    }

    @Override
    public String toString() {
        return "ExporterConfig{" + host + ":" + port + path
                + ", namespace=" + namespace
                + ", auth=" + authEnabled
                + ", metrics=" + enabledMetrics + "}";
    }

    public static final class Builder {
        private String host = "0.0.0.0";
        private int port = 9985;
        private String path = "/metrics";
        private String namespace = "minecraft_proxy";
        private boolean authEnabled;
        private String authUsername = "";
        private String authPassword = "";
        private Duration backendPingInterval = Duration.ofSeconds(30);
        private Duration backendPingTimeout = Duration.ofSeconds(5);
        private Duration playerPingSampleInterval = Duration.ofSeconds(15);
        private int maxSeriesPerMetric = 2000;
        private Set<MetricId> enabledMetrics = MetricId.defaults();
        private boolean bstats = true;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder authEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
            return this;
        }

        public Builder authUsername(String authUsername) {
            this.authUsername = authUsername;
            return this;
        }

        public Builder authPassword(String authPassword) {
            this.authPassword = authPassword;
            return this;
        }

        public Builder backendPingInterval(Duration backendPingInterval) {
            this.backendPingInterval = backendPingInterval;
            return this;
        }

        public Builder backendPingTimeout(Duration backendPingTimeout) {
            this.backendPingTimeout = backendPingTimeout;
            return this;
        }

        public Builder playerPingSampleInterval(Duration playerPingSampleInterval) {
            this.playerPingSampleInterval = playerPingSampleInterval;
            return this;
        }

        public Builder maxSeriesPerMetric(int maxSeriesPerMetric) {
            this.maxSeriesPerMetric = maxSeriesPerMetric;
            return this;
        }

        public Builder enabledMetrics(Set<MetricId> enabledMetrics) {
            this.enabledMetrics = enabledMetrics.isEmpty()
                    ? EnumSet.noneOf(MetricId.class)
                    : EnumSet.copyOf(enabledMetrics);
            return this;
        }

        public Builder enable(MetricId id) {
            Set<MetricId> next = EnumSet.copyOf(enabledMetrics.isEmpty()
                    ? EnumSet.noneOf(MetricId.class) : enabledMetrics);
            next.add(id);
            this.enabledMetrics = next;
            return this;
        }

        public Builder disable(MetricId id) {
            Set<MetricId> next = enabledMetrics.isEmpty()
                    ? EnumSet.noneOf(MetricId.class) : EnumSet.copyOf(enabledMetrics);
            next.remove(id);
            this.enabledMetrics = next;
            return this;
        }

        public Builder bstats(boolean bstats) {
            this.bstats = bstats;
            return this;
        }

        /**
         * Validates and freezes the configuration.
         *
         * @throws ConfigurationException if any value would produce an unusable exporter
         */
        public ExporterConfig build() {
            if (host == null || host.isBlank()) {
                throw new ConfigurationException("host must not be blank");
            }
            // 0 means "bind an ephemeral port", which the JDK server supports and which the
            // test suite relies on to run without fighting over a fixed port.
            if (port < 0 || port > 65535) {
                throw new ConfigurationException("port must be between 0 and 65535, got " + port);
            }
            if (path == null || path.isBlank()) {
                throw new ConfigurationException("path must not be blank");
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (namespace == null) {
                throw new ConfigurationException("namespace must not be null");
            }
            namespace = namespace.trim().toLowerCase(Locale.ROOT);
            // Tolerate "minecraft_proxy_" so a trailing underscore is not a silent double separator.
            while (namespace.endsWith("_")) {
                namespace = namespace.substring(0, namespace.length() - 1);
            }
            if (!VALID_NAMESPACE.matcher(namespace).matches()) {
                throw new ConfigurationException(
                        "namespace '" + namespace + "' is not a valid Prometheus name prefix; "
                                + "expected [a-zA-Z_][a-zA-Z0-9_]*");
            }
            if (authEnabled && (authUsername == null || authUsername.isBlank())) {
                throw new ConfigurationException("auth.username must be set when auth.enabled is true");
            }
            if (authEnabled && (authPassword == null || authPassword.isEmpty())) {
                throw new ConfigurationException("auth.password must be set when auth.enabled is true");
            }
            requireNonNegative(backendPingInterval, "collection.backend-ping-interval-seconds");
            requireNonNegative(playerPingSampleInterval, "collection.player-ping-sample-interval-seconds");
            if (backendPingTimeout == null || backendPingTimeout.isNegative() || backendPingTimeout.isZero()) {
                throw new ConfigurationException("collection.backend-ping-timeout-seconds must be positive");
            }
            if (maxSeriesPerMetric < 1) {
                throw new ConfigurationException("max-series-per-metric must be at least 1");
            }
            if (authUsername == null) {
                authUsername = "";
            }
            if (authPassword == null) {
                authPassword = "";
            }
            return new ExporterConfig(this);
        }

        private static void requireNonNegative(Duration value, String name) {
            if (value == null || value.isNegative()) {
                throw new ConfigurationException(name + " must not be negative");
            }
        }
    }
}
