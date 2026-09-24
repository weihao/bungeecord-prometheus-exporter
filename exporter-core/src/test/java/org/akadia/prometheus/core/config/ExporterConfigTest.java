package org.akadia.prometheus.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExporterConfigTest {

    @Test
    @DisplayName("namespace is normalised to a valid Prometheus prefix")
    void namespaceIsNormalised() {
        assertThat(ExporterConfig.builder().namespace("MyProxy").build().namespace())
                .isEqualTo("myproxy");
        // A trailing underscore would otherwise produce "minecraft_proxy__players_online".
        assertThat(ExporterConfig.builder().namespace("minecraft_proxy_").build().namespace())
                .isEqualTo("minecraft_proxy");
        assertThat(ExporterConfig.builder().namespace("  padded  ").build().namespace())
                .isEqualTo("padded");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9bad", "has-dash", "has.dot", "has space", "", "with:colon"})
    @DisplayName("an invalid namespace is rejected before it can produce broken metric names")
    void invalidNamespacesRejected(String namespace) {
        assertThatThrownBy(() -> ExporterConfig.builder().namespace(namespace).build())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    @DisplayName("metricName joins the namespace and the suffix with a single underscore")
    void metricNameComposition() {
        ExporterConfig config = ExporterConfig.builder().namespace("mc").build();

        assertThat(config.metricName("players_online")).isEqualTo("mc_players_online");
    }

    @Test
    @DisplayName("a path without a leading slash is corrected")
    void pathIsNormalised() {
        assertThat(ExporterConfig.builder().path("metrics").build().path()).isEqualTo("/metrics");
        assertThat(ExporterConfig.builder().path("/custom").build().path()).isEqualTo("/custom");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65536, 100000})
    @DisplayName("an out-of-range port is rejected")
    void portRange(int port) {
        assertThatThrownBy(() -> ExporterConfig.builder().port(port).build())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("port");
    }

    @Test
    @DisplayName("port 0 is allowed and means an ephemeral port")
    void ephemeralPortAllowed() {
        assertThatCode(() -> ExporterConfig.builder().port(0).build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("auth without credentials is rejected rather than silently open")
    void authRequiresCredentials() {
        assertThatThrownBy(() -> ExporterConfig.builder().authEnabled(true).build())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("auth.username");

        assertThatThrownBy(() -> ExporterConfig.builder()
                .authEnabled(true).authUsername("prom").build())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("auth.password");

        assertThatCode(() -> ExporterConfig.builder()
                .authEnabled(true).authUsername("prom").authPassword("secret").build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a negative interval is rejected, zero is not")
    void intervalValidation() {
        assertThatThrownBy(() -> ExporterConfig.builder()
                .backendPingInterval(Duration.ofSeconds(-1)).build())
                .isInstanceOf(ConfigurationException.class);

        assertThatCode(() -> ExporterConfig.builder()
                .backendPingInterval(Duration.ZERO).build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a zero ping timeout is rejected because it can never succeed")
    void timeoutMustBePositive() {
        assertThatThrownBy(() -> ExporterConfig.builder()
                .backendPingTimeout(Duration.ZERO).build())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("the cardinality budget must be at least one series")
    void seriesBudgetValidation() {
        assertThatThrownBy(() -> ExporterConfig.builder().maxSeriesPerMetric(0).build())
                .isInstanceOf(ConfigurationException.class);
    }

    @Test
    @DisplayName("the enabled-metric set is an immutable copy")
    void enabledMetricsAreImmutable() {
        EnumSet<MetricId> source = EnumSet.of(MetricId.JVM);
        ExporterConfig config = ExporterConfig.builder().enabledMetrics(source).build();

        source.add(MetricId.PLAYER_LIST);

        assertThat(config.enabledMetrics()).containsExactly(MetricId.JVM);
        assertThatThrownBy(() -> config.enabledMetrics().add(MetricId.PLUGINS))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("an empty metric set is allowed, producing an endpoint with only self-metrics")
    void everythingCanBeDisabled() {
        ExporterConfig config = ExporterConfig.builder()
                .enabledMetrics(EnumSet.noneOf(MetricId.class)).build();

        assertThat(config.enabledMetrics()).isEmpty();
    }

    @Test
    @DisplayName("toBuilder round-trips every field")
    void toBuilderRoundTrips() {
        ExporterConfig original = ExporterConfig.builder()
                .host("10.1.2.3")
                .port(1234)
                .path("/m")
                .namespace("custom")
                .authEnabled(true).authUsername("u").authPassword("p")
                .backendPingInterval(Duration.ofSeconds(7))
                .backendPingTimeout(Duration.ofSeconds(3))
                .playerPingSampleInterval(Duration.ofSeconds(11))
                .maxSeriesPerMetric(42)
                .enabledMetrics(EnumSet.of(MetricId.EVENTS))
                .bstats(false)
                .build();

        ExporterConfig copy = original.toBuilder().build();

        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("the password is never printed in toString")
    void toStringHidesSecrets() {
        String text = ExporterConfig.builder()
                .authEnabled(true).authUsername("prom").authPassword("hunter2").build().toString();

        assertThat(text).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("high-cardinality groups are off by default")
    void highCardinalityDefaultsOff() {
        assertThat(MetricId.defaults())
                .doesNotContain(MetricId.PLAYER_LIST,
                        MetricId.PLAYER_PING_PER_PLAYER,
                        MetricId.COMMAND_LABEL);

        for (MetricId id : MetricId.values()) {
            if (id.highCardinality()) {
                assertThat(id.enabledByDefault())
                        .as("%s is high cardinality so it must default to off", id.key())
                        .isFalse();
            }
        }
    }
}
