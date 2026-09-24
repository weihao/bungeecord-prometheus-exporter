package org.akadia.prometheus.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification: the shipped jar, loaded by real proxies, scraped over real HTTP,
 * and validated by the real promtool.
 *
 * <p>Skipped automatically when Docker is not available, so the unit suite still runs on a
 * developer machine without it. CI always has Docker, so these always run there.
 */
@EnabledIf("dockerAvailable")
class ProxyExporterIT {

    private static final String NS = "minecraft_proxy_";

    private static Path pluginJar;
    private static PromtoolValidator promtool;

    @SuppressWarnings("unused")
    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @BeforeAll
    static void setUp() {
        pluginJar = ProxyContainer.pluginJar();
        promtool = new PromtoolValidator();
    }

    @AfterAll
    static void tearDown() {
        if (promtool != null) {
            promtool.close();
        }
    }

    /** Both proxy families, from one jar. */
    static Stream<org.junit.jupiter.params.provider.Arguments> proxies() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("Velocity",
                        (Function<Path, ProxyContainer>) ProxyContainer::velocity),
                org.junit.jupiter.params.provider.Arguments.of("BungeeCord",
                        (Function<Path, ProxyContainer>) ProxyContainer::bungeecord),
                org.junit.jupiter.params.provider.Arguments.of("Waterfall",
                        (Function<Path, ProxyContainer>) ProxyContainer::waterfall));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("proxies")
    @DisplayName("the plugin loads and serves valid metrics")
    void pluginServesMetrics(String name, Function<Path, ProxyContainer> factory) throws Exception {
        try (ProxyContainer proxy = factory.apply(pluginJar)) {
            proxy.start();

            // 1. The endpoint answers at all, which proves the descriptor parsed, the main
            //    class loaded, the relocated dependencies resolved and the port bound.
            assertThat(proxy.get("/metrics").statusCode()).isEqualTo(200);
            String exposition = proxy.fetchMetrics();

            // 2. The real Prometheus parser and linter accept the output.
            PromtoolValidator.Result result = promtool.checkMetrics(exposition);
            assertThat(result.ok())
                    .as("promtool check metrics rejected the output:%n%s", result.output())
                    .isTrue();

            // 3. The metrics an operator depends on are present with sane values.
            assertThat(exposition)
                    .contains(NS + "players_online")
                    .contains(NS + "info{")
                    .contains(NS + "exporter_build_info{")
                    .contains(NS + "servers_registered")
                    .contains(NS + "plugins_installed")
                    .contains(NS + "uptime_seconds")
                    .contains("jvm_memory_used_bytes")
                    .contains("process_cpu_seconds_total");

            // 4. The proxy reports itself correctly through the info metric.
            assertThat(exposition).containsPattern(
                    NS + "info\\{[^}]*type=\"" + (name.equals("Velocity") ? "velocity" : "bungeecord")
                            + "\"");

            // 5. No collector failed while serving that scrape.
            assertThat(exposition).doesNotContain(NS + "exporter_scrape_errors_total{");

            // 6. The health endpoint answers, and unknown paths do not leak metrics.
            assertThat(proxy.get("/-/healthy").statusCode()).isEqualTo(200);
            assertThat(proxy.get("/").statusCode()).isEqualTo(404);

            // 7. The plugin sees itself in the proxy's plugin list.
            assertThat(exposition).contains(NS + "plugin_info{");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("proxies")
    @DisplayName("the proxy logs no exception from the exporter during startup")
    void startupIsClean(String name, Function<Path, ProxyContainer> factory) {
        try (ProxyContainer proxy = factory.apply(pluginJar)) {
            proxy.start();
            proxy.fetchMetrics();

            String logs = proxy.getLogs();

            // A relocation or descriptor mistake shows up here long before it shows up as a
            // missing metric.
            assertThat(logs).doesNotContain("NoClassDefFoundError");
            assertThat(logs).doesNotContain("ClassNotFoundException");
            assertThat(logs).doesNotContain("UnsupportedClassVersionError");
            assertThat(logs).doesNotContain("Invalid configuration");
            assertThat(logs).doesNotContain("Could not bind the metrics endpoint");
            assertThat(logs).contains("Prometheus metrics available at");
        }
    }

    @Test
    @DisplayName("environment overrides reach the plugin inside the container")
    void environmentOverridesApply() {
        // This is the path a Docker or Kubernetes operator actually uses, and it only works
        // if the env-var naming scheme survives into the running plugin.
        try (ProxyContainer proxy = ProxyContainer.velocity(pluginJar)
                .withEnv("MC_PROXY_EXPORTER_NAMESPACE", "custom_ns")
                .withEnv("MC_PROXY_EXPORTER_PATH", "/internal/metrics")
                .withEnv("MC_PROXY_EXPORTER_METRICS_JVM", "false")) {
            proxy.start();

            assertThat(proxy.get("/internal/metrics").statusCode()).isEqualTo(200);
            assertThat(proxy.get("/metrics").statusCode()).isEqualTo(404);

            String exposition = proxy.get("/internal/metrics").body();
            assertThat(exposition).contains("custom_ns_players_online");
            assertThat(exposition).doesNotContain(NS + "players_online");
            assertThat(exposition).doesNotContain("jvm_memory_used_bytes");
        }
    }

    @Test
    @DisplayName("basic auth set from the environment is enforced")
    void basicAuthApplies() {
        try (ProxyContainer proxy = ProxyContainer.velocity(pluginJar)
                .withEnv("MC_PROXY_EXPORTER_AUTH_ENABLED", "true")
                .withEnv("MC_PROXY_EXPORTER_AUTH_USERNAME", "prometheus")
                .withEnv("MC_PROXY_EXPORTER_AUTH_PASSWORD", "s3cret")) {
            proxy.start();

            assertThat(proxy.get("/metrics").statusCode()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("the default config file is written into the plugin's data directory")
    void defaultConfigIsWritten() throws Exception {
        try (ProxyContainer proxy = ProxyContainer.velocity(pluginJar)) {
            proxy.start();
            proxy.fetchMetrics();

            org.testcontainers.containers.Container.ExecResult result = proxy.execInContainer(
                    "/bin/sh", "-c", "find /server -name config.yml -path '*prometheus*' | head -1");
            String path = result.getStdout().trim();
            assertThat(path).as("a default config.yml must be created on first start").isNotEmpty();

            String contents = proxy.execInContainer("cat", path).getStdout();
            assertThat(contents)
                    .contains("namespace: minecraft_proxy")
                    .contains("MC_PROXY_EXPORTER_PORT")
                    .contains("max-series-per-metric");
        }
    }

    @Test
    @DisplayName("content negotiation returns well-formed OpenMetrics")
    void openMetricsIsWellFormed() throws Exception {
        try (ProxyContainer proxy = ProxyContainer.velocity(pluginJar)) {
            proxy.start();
            proxy.fetchMetrics();

            // Content negotiation is what a modern Prometheus actually uses.
            java.net.http.HttpResponse<String> response =
                    java.net.http.HttpClient.newHttpClient().send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                                            "http://" + proxy.getHost() + ":"
                                                    + proxy.getMappedPort(ProxyContainer.METRICS_PORT)
                                                    + "/metrics"))
                                    .header("Accept", "application/openmetrics-text; version=1.0.0")
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type"))
                    .hasValueSatisfying(type -> assertThat(type).contains("openmetrics-text"));

            String body = response.body();
            // OpenMetrics requires the terminating marker, carries UNIT lines that the
            // classic format does not, and types _info metrics distinctly.
            assertThat(body).endsWith("# EOF\n");
            assertThat(body).contains("# UNIT " + NS + "uptime_seconds seconds");
            // In OpenMetrics the _info suffix belongs to the sample name, not the family
            // name, so the TYPE line reads "minecraft_proxy info" while the sample is
            // "minecraft_proxy_info{...}". Both are asserted so a regression in either
            // half is caught.
            assertThat(body).contains("# TYPE minecraft_proxy info");
            assertThat(body).contains(NS + "info{");
            assertThat(body).contains(NS + "players_online");

            // Deliberately NOT run through "promtool check metrics": that command parses
            // the classic Prometheus text format, which has no "info" metric type, so it
            // rejects valid OpenMetrics. The classic rendering is the one promtool checks,
            // in pluginServesMetrics above.
        }
    }

    @Test
    @DisplayName("the jar carries a descriptor for both proxies and Java 17 bytecode")
    void jarIsPortable() throws Exception {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(pluginJar.toFile())) {
            assertThat(zip.getEntry("bungee.yml")).as("BungeeCord descriptor").isNotNull();
            assertThat(zip.getEntry("velocity-plugin.json")).as("Velocity descriptor").isNotNull();
            assertThat(zip.getEntry("config.yml")).as("bundled default config").isNotNull();

            // Class file 61 is Java 17. Anything higher would refuse to load on a
            // BungeeCord or Velocity 3.4 proxy.
            List<String> tooNew = zip.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().startsWith("META-INF/versions/"))
                    .filter(entry -> majorVersion(zip, entry) > 61)
                    .map(java.util.zip.ZipEntry::getName)
                    .limit(10)
                    .toList();
            assertThat(tooNew).as("every class must be Java 17 bytecode or older").isEmpty();
        }
    }

    private static int majorVersion(java.util.zip.ZipFile zip, java.util.zip.ZipEntry entry) {
        try (java.io.DataInputStream in =
                     new java.io.DataInputStream(zip.getInputStream(entry))) {
            if (in.readInt() != 0xCAFEBABE) {
                return 0;
            }
            in.readUnsignedShort();
            return in.readUnsignedShort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read " + entry.getName(), e);
        }
    }
}
