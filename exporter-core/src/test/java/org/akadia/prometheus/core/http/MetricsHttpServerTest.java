package org.akadia.prometheus.core.http;

import org.akadia.prometheus.core.ProxyExporter;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real endpoint over real HTTP, the way Prometheus will. */
class MetricsHttpServerTest {

    private static final String NS = "minecraft_proxy_";

    @Test
    @DisplayName("the metrics path serves the Prometheus text format")
    void servesPrometheusText() {
        try (ExporterHarness harness = ExporterHarness.start(platformWithData())) {
            HttpResponse<String> response = harness.get("/metrics");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type"))
                    .hasValueSatisfying(type -> assertThat(type).contains("text/plain"));
            assertThat(response.body()).contains("# HELP " + NS + "players_online");
            assertThat(response.body()).contains("# TYPE " + NS + "players_online gauge");
        }
    }

    @Test
    @DisplayName("an OpenMetrics Accept header gets the OpenMetrics format")
    void contentNegotiation() {
        try (ExporterHarness harness = ExporterHarness.start(platformWithData())) {
            HttpResponse<String> response = harness.get(
                    "/metrics", "application/openmetrics-text; version=1.0.0", null, null);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type"))
                    .hasValueSatisfying(type -> assertThat(type).contains("openmetrics-text"));
            assertThat(response.body()).endsWith("# EOF\n");
        }
    }

    @Test
    @DisplayName("the HTTP body parses back to the same values as a direct scrape")
    void httpMatchesDirectScrape() {
        try (ExporterHarness harness = ExporterHarness.start(platformWithData())) {
            ScrapedMetrics overHttp = harness.fetchMetrics();

            assertThat(overHttp.value(NS + "players_online")).contains(2.0d);
            assertThat(overHttp.value(NS + "servers_registered")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("a custom metrics path is honoured and the default path stops responding")
    void customPath() {
        try (ExporterHarness harness = ExporterHarness.start(
                platformWithData(), builder -> builder.path("/internal/metrics"))) {
            assertThat(harness.get("/internal/metrics").statusCode()).isEqualTo(200);
            assertThat(harness.get("/metrics").statusCode()).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("an unknown path is a 404, not a scrape")
    void unknownPathIs404() {
        try (ExporterHarness harness = ExporterHarness.start(platformWithData())) {
            assertThat(harness.get("/").statusCode()).isEqualTo(404);
            assertThat(harness.get("/admin").statusCode()).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("the health endpoint answers without running the collectors")
    void healthEndpoint() {
        // An orchestrator probing liveness every second should not pay for a full scrape,
        // and a proxy with a broken collector should still report itself alive.
        FakeProxyPlatform platform = platformWithData()
                .failPlayersWith(() -> new IllegalStateException("boom"));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            HttpResponse<String> response = harness.get("/-/healthy");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(platform.playerCountCalls())
                    .as("the health check must not trigger metric collection")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("basic auth")
    class BasicAuth {

        @Test
        @DisplayName("credentials are required when auth is enabled")
        void requiresCredentials() {
            try (ExporterHarness harness = ExporterHarness.start(platformWithData(), builder -> builder
                    .authEnabled(true).authUsername("prometheus").authPassword("s3cret"))) {
                assertThat(harness.get("/metrics").statusCode()).isEqualTo(401);
            }
        }

        @Test
        @DisplayName("correct credentials are accepted")
        void acceptsCorrectCredentials() {
            try (ExporterHarness harness = ExporterHarness.start(platformWithData(), builder -> builder
                    .authEnabled(true).authUsername("prometheus").authPassword("s3cret"))) {
                HttpResponse<String> response =
                        harness.get("/metrics", null, "prometheus", "s3cret");

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains(NS + "players_online");
            }
        }

        @Test
        @DisplayName("a wrong password is rejected")
        void rejectsWrongPassword() {
            try (ExporterHarness harness = ExporterHarness.start(platformWithData(), builder -> builder
                    .authEnabled(true).authUsername("prometheus").authPassword("s3cret"))) {
                assertThat(harness.get("/metrics", null, "prometheus", "wrong").statusCode())
                        .isEqualTo(401);
            }
        }

        @Test
        @DisplayName("a wrong username is rejected")
        void rejectsWrongUsername() {
            try (ExporterHarness harness = ExporterHarness.start(platformWithData(), builder -> builder
                    .authEnabled(true).authUsername("prometheus").authPassword("s3cret"))) {
                assertThat(harness.get("/metrics", null, "someone", "s3cret").statusCode())
                        .isEqualTo(401);
            }
        }

        @Test
        @DisplayName("the health endpoint is behind auth too, which probes must account for")
        void healthEndpointIsAlsoProtected() {
            // The Prometheus client applies the authenticator to every context it
            // registers, including /-/healthy. That is the safer default - no
            // unauthenticated surface - but it means a Kubernetes liveness probe or Docker
            // HEALTHCHECK has to send credentials or it will see 401 and restart the
            // proxy. Pinned here so the behaviour is a documented property rather than a
            // surprise, and so a library change that silently opens it up is caught.
            try (ExporterHarness harness = ExporterHarness.start(platformWithData(), builder -> builder
                    .authEnabled(true).authUsername("prometheus").authPassword("s3cret"))) {
                assertThat(harness.get("/-/healthy").statusCode()).isEqualTo(401);
                assertThat(harness.get("/-/healthy", null, "prometheus", "s3cret").statusCode())
                        .isEqualTo(200);
            }
        }

        @Test
        @DisplayName("a password with surrounding whitespace authenticates as written")
        void passwordWhitespaceIsSignificant() {
            try (ExporterHarness harness = ExporterHarness.start(platformWithData(), builder -> builder
                    .authEnabled(true).authUsername("prometheus").authPassword("  spaced  "))) {
                assertThat(harness.get("/metrics", null, "prometheus", "  spaced  ").statusCode())
                        .isEqualTo(200);
                assertThat(harness.get("/metrics", null, "prometheus", "spaced").statusCode())
                        .isEqualTo(401);
            }
        }

        @Test
        @DisplayName("credential comparison does not short-circuit on the first byte")
        void credentialsAreComparedInConstantTime() {
            // A byte-at-a-time comparison leaks the secret to anyone who can time the
            // response, and a metrics endpoint is routinely more reachable than intended.
            ConstantTimeBasicAuthenticator authenticator =
                    new ConstantTimeBasicAuthenticator("metrics", "prometheus", "s3cret");

            assertThat(authenticator.checkCredentials("prometheus", "s3cret")).isTrue();
            assertThat(authenticator.checkCredentials("prometheus", "s3cre")).isFalse();
            assertThat(authenticator.checkCredentials("prometheus", "s3cretX")).isFalse();
            assertThat(authenticator.checkCredentials("Prometheus", "s3cret")).isFalse();
            assertThat(authenticator.checkCredentials(null, "s3cret")).isFalse();
            assertThat(authenticator.checkCredentials("prometheus", null)).isFalse();
        }
    }

    @Test
    @DisplayName("a port already in use produces a clear failure rather than a silent no-op")
    void portConflictIsReported() throws IOException {
        try (ServerSocket occupied = new ServerSocket(0)) {
            FakeProxyPlatform platform = platformWithData();
            ExporterConfig config = ExporterConfig.builder()
                    .host("127.0.0.1")
                    .port(occupied.getLocalPort())
                    .build();

            assertThatThrownBy(() -> ProxyExporter.builder(config, platform).start())
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    @DisplayName("a failed start leaves no sampler threads behind")
    void failedStartCleansUp() throws Exception {
        // The samplers are scheduled before the port is bound, so a bind failure must
        // unwind them. Otherwise a reload that hit a still-bound port would leave daemon
        // threads pinging every backend forever with no endpoint serving the results.
        FakeBackendServer lobby = FakeBackendServer.named("lobby");
        FakeProxyPlatform platform = new FakeProxyPlatform().addServer(lobby);

        try (ServerSocket occupied = new ServerSocket(0)) {
            ExporterConfig config = ExporterConfig.builder()
                    .host("127.0.0.1")
                    .port(occupied.getLocalPort())
                    .backendPingInterval(java.time.Duration.ofMillis(100))
                    .build();

            assertThatThrownBy(() -> ProxyExporter.builder(config, platform).start())
                    .isInstanceOf(IOException.class);

            int afterFailure = lobby.pingCount();
            Thread.sleep(500);
            assertThat(lobby.pingCount())
                    .as("no sampler may survive a failed start")
                    .isEqualTo(afterFailure);
        }
    }

    @Test
    @DisplayName("stopping releases the port so a reload can rebind it")
    void portIsReleasedOnClose() throws IOException {
        // The 3.x plugin never stopped its server, so /reload left the port bound and the
        // exporter dead until the proxy was restarted.
        FakeProxyPlatform platform = platformWithData();
        ExporterConfig config = ExporterConfig.builder().host("127.0.0.1").port(0).build();

        ProxyExporter first = ProxyExporter.builder(config, platform).start();
        int port = first.port();
        first.close();

        ExporterConfig rebind = config.toBuilder().port(port).build();
        ProxyExporter second = ProxyExporter.builder(rebind, platform).start();
        try {
            assertThat(second.port()).isEqualTo(port);
        } finally {
            second.close();
        }
    }

    @Test
    @DisplayName("an ephemeral port is reported back after binding")
    void ephemeralPortIsResolved() {
        try (ExporterHarness harness = ExporterHarness.start(platformWithData())) {
            assertThat(harness.exporter().config().port()).isZero();
            assertThat(harness.port()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("the endpoint stops answering after close")
    void endpointStopsAfterClose() throws IOException {
        FakeProxyPlatform platform = platformWithData();
        ExporterHarness harness = ExporterHarness.start(platform);
        int port = harness.port();
        assertThat(harness.get("/metrics").statusCode()).isEqualTo(200);

        harness.close();

        // Rebinding the same port proves nothing is still listening on it.
        try (ServerSocket rebound = new ServerSocket(port)) {
            assertThat(rebound.getLocalPort()).isEqualTo(port);
        }
    }

    private static FakeProxyPlatform platformWithData() {
        return new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addPlayer("Notch", "lobby", 42)
                .addPlayer("Herobrine", "lobby", 80)
                .addPlugin("proxy-prometheus-exporter", "4.0.0");
    }
}
