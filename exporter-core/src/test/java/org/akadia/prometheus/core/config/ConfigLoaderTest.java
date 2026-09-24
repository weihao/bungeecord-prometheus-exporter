package org.akadia.prometheus.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    private final List<String> warnings = new ArrayList<>();

    @Test
    @DisplayName("defaults are usable with no file and no overrides")
    void defaultsAreUsable() {
        ExporterConfig config = ConfigLoader.from(ConfigSource.of(Map.of(), Map.of(), Map.of()), warnings::add);

        assertThat(config.host()).isEqualTo("0.0.0.0");
        assertThat(config.port()).isEqualTo(9985);
        assertThat(config.path()).isEqualTo("/metrics");
        assertThat(config.namespace()).isEqualTo("minecraft_proxy");
        assertThat(config.authEnabled()).isFalse();
        assertThat(config.enabledMetrics()).isEqualTo(MetricId.defaults());
        assertThat(warnings).isEmpty();
    }

    @Nested
    @DisplayName("precedence")
    class Precedence {

        @Test
        @DisplayName("environment beats system property beats file")
        void environmentWins() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of("MC_PROXY_EXPORTER_PORT", "1111"),
                    Map.of("mc.proxy.exporter.port", "2222"),
                    Map.of("port", 3333)), warnings::add);

            assertThat(config.port()).isEqualTo(1111);
        }

        @Test
        @DisplayName("system property beats file")
        void systemPropertyWins() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(),
                    Map.of("mc.proxy.exporter.port", "2222"),
                    Map.of("port", 3333)), warnings::add);

            assertThat(config.port()).isEqualTo(2222);
        }

        @Test
        @DisplayName("file beats the built-in default")
        void fileWins() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(), Map.of("port", 3333)), warnings::add);

            assertThat(config.port()).isEqualTo(3333);
        }

        @Test
        @DisplayName("a password keeps its surrounding whitespace")
        void passwordIsNotTrimmed() {
            // Trimming a secret produces a 401 that looks like the wrong credentials were
            // configured, with nothing in the logs to explain it.
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of("MC_PROXY_EXPORTER_AUTH_ENABLED", "true",
                            "MC_PROXY_EXPORTER_AUTH_USERNAME", "prometheus",
                            "MC_PROXY_EXPORTER_AUTH_PASSWORD", "  hunter2  "),
                    Map.of(), Map.of()), warnings::add);

            assertThat(config.authPassword()).isEqualTo("  hunter2  ");
            assertThat(config.authUsername()).isEqualTo("prometheus");
        }

        @Test
        @DisplayName("a password from the config file keeps its whitespace too")
        void passwordFromFileIsNotTrimmed() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(),
                    Map.of("auth", Map.of("enabled", true, "username", "u",
                            "password", " spaced "))), warnings::add);

            assertThat(config.authPassword()).isEqualTo(" spaced ");
        }

        @Test
        @DisplayName("a blank override is ignored rather than clearing the value")
        void blankOverrideIsIgnored() {
            // Docker Compose renders an unset variable as an empty string; treating that as
            // "set to nothing" would silently blank out the host.
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of("MC_PROXY_EXPORTER_HOST", "   "),
                    Map.of(),
                    Map.of("host", "10.0.0.5")), warnings::add);

            assertThat(config.host()).isEqualTo("10.0.0.5");
        }
    }

    @Nested
    @DisplayName("environment variable naming")
    class EnvironmentNaming {

        @ParameterizedTest
        @CsvSource({
                "port,                                      MC_PROXY_EXPORTER_PORT",
                "auth.enabled,                              MC_PROXY_EXPORTER_AUTH_ENABLED",
                "metrics.players-online,                    MC_PROXY_EXPORTER_METRICS_PLAYERS_ONLINE",
                "collection.backend-ping-interval-seconds,  MC_PROXY_EXPORTER_COLLECTION_BACKEND_PING_INTERVAL_SECONDS"
        })
        @DisplayName("dots and dashes both become underscores")
        void envKeysAreDerivedFromPaths(String path, String expected) {
            assertThat(ConfigSource.envKey(path.trim())).isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("nested metric toggles can be set from the environment")
        void nestedTogglesFromEnvironment() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of("MC_PROXY_EXPORTER_METRICS_JVM", "false",
                            "MC_PROXY_EXPORTER_METRICS_PLAYER_LIST", "true"),
                    Map.of(), Map.of()), warnings::add);

            assertThat(config.isEnabled(MetricId.JVM)).isFalse();
            assertThat(config.isEnabled(MetricId.PLAYER_LIST)).isTrue();
        }
    }

    @Nested
    @DisplayName("value parsing")
    class ValueParsing {

        @ParameterizedTest
        @ValueSource(strings = {"true", "TRUE", "yes", "on", "1", "enabled"})
        @DisplayName("truthy spellings are all accepted")
        void truthyValues(String raw) {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(), Map.of("metrics", Map.of("player-list", raw))), warnings::add);

            assertThat(config.isEnabled(MetricId.PLAYER_LIST)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "FALSE", "no", "off", "0", "disabled"})
        @DisplayName("falsy spellings are all accepted")
        void falsyValues(String raw) {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(), Map.of("metrics", Map.of("jvm", raw))), warnings::add);

            assertThat(config.isEnabled(MetricId.JVM)).isFalse();
        }

        @Test
        @DisplayName("a nonsense boolean names the offending key")
        void badBooleanIsReported() {
            assertThatThrownBy(() -> ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(), Map.of("bstats", "maybe")), warnings::add))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("bstats")
                    .hasMessageContaining("maybe");
        }

        @Test
        @DisplayName("a nonsense number names the offending key")
        void badNumberIsReported() {
            assertThatThrownBy(() -> ConfigLoader.from(ConfigSource.of(
                    Map.of("MC_PROXY_EXPORTER_PORT", "nine-thousand"), Map.of(), Map.of()), warnings::add))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("port")
                    .hasMessageContaining("nine-thousand");
        }

        @Test
        @DisplayName("fractional intervals are honoured")
        void fractionalSeconds() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(),
                    Map.of("collection", Map.of("backend-ping-interval-seconds", 2.5))), warnings::add);

            assertThat(config.backendPingInterval()).isEqualTo(Duration.ofMillis(2500));
        }

        @Test
        @DisplayName("a zero interval disables the sampler rather than failing")
        void zeroIntervalIsAllowed() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(),
                    Map.of("collection", Map.of("backend-ping-interval-seconds", 0))), warnings::add);

            assertThat(config.backendPingInterval()).isZero();
        }
    }

    @Nested
    @DisplayName("operator guidance")
    class OperatorGuidance {

        @Test
        @DisplayName("enabling a high-cardinality metric warns about it")
        void highCardinalityWarns() {
            ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(), Map.of("metrics", Map.of("player-list", true))), warnings::add);

            assertThat(warnings).anyMatch(warning ->
                    warning.contains("player-list") && warning.contains("per player"));
        }

        @Test
        @DisplayName("a misspelled metric key is reported with the valid options")
        void unknownKeyWarns() {
            ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(), Map.of("metrics", Map.of("playerz_online", true))), warnings::add);

            assertThat(warnings).anyMatch(warning ->
                    warning.contains("playerz_online") && warning.contains("players-online"));
        }

        @Test
        @DisplayName("underscores are accepted where the docs use dashes")
        void underscoreAliasIsAccepted() {
            ExporterConfig config = ConfigLoader.from(ConfigSource.of(
                    Map.of(), Map.of(), Map.of("metrics", Map.of("player_list", true))), warnings::add);

            assertThat(config.isEnabled(MetricId.PLAYER_LIST)).isTrue();
            assertThat(warnings).noneMatch(warning -> warning.contains("unknown key"));
        }
    }

    @Nested
    @DisplayName("file handling")
    class FileHandling {

        @Test
        @DisplayName("the bundled default config is written on first start and is valid")
        void writesAndReadsDefaultConfig(@TempDir Path dataDirectory) {
            ExporterConfig config = ConfigLoader.load(dataDirectory, warnings::add);

            Path file = dataDirectory.resolve(ConfigLoader.CONFIG_FILE_NAME);
            assertThat(file).exists();
            assertThat(config.port()).isEqualTo(9985);
            // The shipped file must not silently differ from the compiled-in defaults.
            assertThat(config.enabledMetrics()).isEqualTo(MetricId.defaults());
            assertThat(warnings).isEmpty();
        }

        @Test
        @DisplayName("an existing config is not overwritten")
        void existingConfigIsPreserved(@TempDir Path dataDirectory) throws IOException {
            Path file = dataDirectory.resolve(ConfigLoader.CONFIG_FILE_NAME);
            Files.writeString(file, "port: 9999\n");

            ExporterConfig config = ConfigLoader.load(dataDirectory, warnings::add);

            assertThat(config.port()).isEqualTo(9999);
            assertThat(Files.readString(file)).isEqualTo("port: 9999\n");
        }

        @Test
        @DisplayName("a missing data directory is created")
        void createsDataDirectory(@TempDir Path parent) {
            Path dataDirectory = parent.resolve("nested").resolve("plugin-data");

            ConfigLoader.load(dataDirectory, warnings::add);

            assertThat(dataDirectory).isDirectory();
        }

        @Test
        @DisplayName("an unreadable config falls back to defaults instead of killing the proxy")
        void unreadableConfigFallsBack(@TempDir Path parent) throws IOException {
            // A directory where the file should be: readable path, unreadable content.
            Path dataDirectory = parent.resolve("data");
            Files.createDirectories(dataDirectory.resolve(ConfigLoader.CONFIG_FILE_NAME));

            ExporterConfig config = ConfigLoader.load(dataDirectory, warnings::add);

            assertThat(config.port()).isEqualTo(9985);
            assertThat(warnings).anyMatch(warning -> warning.contains("continuing with defaults"));
        }

        @Test
        @DisplayName("an empty file is treated as all defaults")
        void emptyYamlIsEmptyMap() {
            assertThat(ConfigLoader.parse(new StringReader(""))).isEmpty();
            assertThat(ConfigLoader.parse(new StringReader("# only a comment\n"))).isEmpty();
        }

        @Test
        @DisplayName("a YAML list at the top level is rejected with a clear message")
        void nonMappingYamlIsRejected() {
            assertThatThrownBy(() -> ConfigLoader.parse(new StringReader("- a\n- b\n")))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("mapping");
        }

        @Test
        @DisplayName("duplicate keys are an error rather than a silent last-one-wins")
        void duplicateKeysRejected() {
            assertThatThrownBy(() -> ConfigLoader.parse(new StringReader("port: 1\nport: 2\n")))
                    .isInstanceOf(Exception.class);
        }
    }
}
