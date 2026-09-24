package org.akadia.prometheus.core;

import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.NetworkStatsProvider;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyExporterLifecycleTest {

    private static final String NS = "minecraft_proxy_";

    @Test
    @DisplayName("enable, disable and enable again works, as a proxy reload requires")
    void repeatedEnableDisable() throws IOException {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addPlayer("Notch", "lobby", 30);
        ExporterConfig config = ExporterConfig.builder().host("127.0.0.1").port(0).build();

        for (int cycle = 0; cycle < 5; cycle++) {
            try (ProxyExporter exporter = ProxyExporter.builder(config, platform).start()) {
                ScrapedMetrics scrape = ScrapedMetrics.from(exporter.registry());
                assertThat(scrape.value(NS + "players_online"))
                        .as("cycle %d", cycle)
                        .contains(1.0d);
            }
        }
    }

    @Test
    @DisplayName("each instance gets its own registry, so a restart cannot collide")
    void registriesAreIsolated() throws IOException {
        // Registering onto the JVM-wide default registry, as 3.x did, meant a second
        // enable threw "collector already registered" and left the proxy without metrics.
        FakeProxyPlatform platform = new FakeProxyPlatform();
        ExporterConfig config = ExporterConfig.builder().host("127.0.0.1").port(0).build();

        try (ProxyExporter first = ProxyExporter.builder(config, platform).start();
             ProxyExporter second = ProxyExporter.builder(config, platform).start()) {
            assertThat(first.registry()).isNotSameAs(second.registry());
            assertThat(first.port()).isNotEqualTo(second.port());
        }
    }

    @Test
    @DisplayName("closing twice is harmless")
    void doubleCloseIsSafe() throws IOException {
        ProxyExporter exporter = ProxyExporter.builder(
                ExporterConfig.builder().host("127.0.0.1").port(0).build(),
                new FakeProxyPlatform()).start();

        exporter.close();
        exporter.close();
    }

    @Test
    @Timeout(30)
    @DisplayName("closing stops the sampler threads")
    void samplerThreadsStop() throws Exception {
        FakeBackendServer lobby = FakeBackendServer.named("lobby");
        FakeProxyPlatform platform = new FakeProxyPlatform().addServer(lobby);
        ExporterConfig config = ExporterConfig.builder()
                .host("127.0.0.1").port(0)
                .backendPingInterval(Duration.ofMillis(100))
                .playerPingSampleInterval(Duration.ZERO)
                .build();

        ProxyExporter exporter = ProxyExporter.builder(config, platform).start();
        // The first run has no delay, so a ping has definitely happened by now.
        Thread.sleep(400);
        int whileRunning = lobby.pingCount();
        assertThat(whileRunning).isPositive();

        exporter.close();
        int atClose = lobby.pingCount();
        Thread.sleep(400);

        assertThat(lobby.pingCount())
                .as("no sampler may keep running after close")
                .isEqualTo(atClose);
    }

    @Test
    @Timeout(30)
    @DisplayName("the first sampler run happens immediately, not after one interval")
    void firstSampleIsImmediate() throws Exception {
        // Otherwise server_up would not exist for the first 30 s after a restart, and an
        // alert cannot fire on a series that is absent.
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"));
        ExporterConfig config = ExporterConfig.builder()
                .host("127.0.0.1").port(0)
                .backendPingInterval(Duration.ofMinutes(30))
                .build();

        try (ProxyExporter exporter = ProxyExporter.builder(config, platform).start()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            ScrapedMetrics scrape = ScrapedMetrics.from(exporter.registry());
            while (scrape.value(NS + "server_up", "server", "lobby").isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.sleep(25);
                scrape = ScrapedMetrics.from(exporter.registry());
            }

            assertThat(scrape.value(NS + "server_up", "server", "lobby")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("uptime is reported from the injected clock")
    void uptimeIsTracked() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

        try (ExporterHarness harness = ExporterHarness.start(
                new FakeProxyPlatform(), builder -> builder, null, clock)) {
            assertThat(harness.scrape().value(NS + "start_time_seconds"))
                    .contains(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli() / 1000.0d);
            assertThat(harness.scrape().value(NS + "uptime_seconds")).contains(0.0d);

            clock.advance(Duration.ofMinutes(90));

            assertThat(harness.scrape().value(NS + "uptime_seconds")).contains(5400.0d);
        }
    }

    @Test
    @DisplayName("proxy identity is exported as labels on the info metric")
    void proxyInfo() {
        FakeProxyPlatform platform = new FakeProxyPlatform().identity(
                new org.akadia.prometheus.core.platform.ProxyIdentity(
                        "bungeecord", "Waterfall", "1.21-R0.4", false));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            ScrapedMetrics.Sample info = harness.scrape().samplesNamed(NS + "info").stream()
                    .findFirst().orElseThrow();

            assertThat(info.value()).isEqualTo(1.0d);
            assertThat(info.labels())
                    .containsEntry("type", "bungeecord")
                    .containsEntry("name", "Waterfall")
                    .containsEntry("version", "1.21-R0.4")
                    .containsEntry("online_mode", "false");
        }
    }

    @Test
    @DisplayName("build information is exported")
    void buildInfo() {
        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform())) {
            ScrapedMetrics.Sample build = harness.scrape()
                    .samplesNamed(NS + "exporter_build_info").stream().findFirst().orElseThrow();

            assertThat(build.labels()).containsKeys(
                    "version", "java_target", "prometheus_client", "velocity_api", "bungeecord_api");
            assertThat(build.labels().get("java_target")).isEqualTo("17");
            assertThat(build.labels().get("prometheus_client")).isNotEqualTo("unknown");
        }
    }

    @Test
    @DisplayName("disabling every metric group still serves a valid, minimal endpoint")
    void everythingDisabled() {
        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform(),
                builder -> builder.enabledMetrics(EnumSet.noneOf(MetricId.class)))) {
            assertThat(harness.get("/metrics").statusCode()).isEqualTo(200);

            ScrapedMetrics scrape = harness.scrape();
            // Self-metrics are always present; they are how you tell a disabled exporter
            // from a broken one.
            assertThat(scrape.hasSample(NS + "exporter_build_info")).isTrue();
            assertThat(scrape.hasSample(NS + "players_online")).isFalse();
            assertThat(scrape.sampleNames()).noneMatch(name -> name.startsWith("jvm_"));
        }
    }

    @Test
    @DisplayName("network metrics are only registered when a coordinator is supplied")
    void networkMetricsRequireAProvider() {
        NetworkStatsProvider provider = new NetworkStatsProvider() {
            @Override
            public int networkPlayerCount() {
                return 250;
            }

            @Override
            public int proxyCount() {
                return 4;
            }
        };

        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform(),
                builder -> builder.enable(MetricId.REDISBUNGEE), provider, Clock.systemUTC())) {
            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "redis_players_online")).contains(250.0d);
            assertThat(scrape.value(NS + "redis_proxies_online")).contains(4.0d);
        }
    }

    @Test
    @DisplayName("enabling redisbungee without a coordinator warns instead of failing")
    void redisBungeeWithoutProviderWarns() {
        FakeProxyPlatform platform = new FakeProxyPlatform();

        try (ExporterHarness harness = ExporterHarness.start(platform,
                builder -> builder.enable(MetricId.REDISBUNGEE))) {
            assertThat(platform.recordingLogger().warnedAbout("no network coordinator")).isTrue();
            assertThat(harness.scrape().hasSample(NS + "redis_players_online")).isFalse();
            assertThat(harness.get("/metrics").statusCode()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("a coordinator that is not available yet emits no series, not a zero")
    void unavailableCoordinatorEmitsNothing() {
        // RedisBungee can enable after this plugin. Reporting 0 players network-wide in
        // that window would be actively wrong; an absent series is honest, and the metric
        // starts reporting by itself once the coordinator comes up.
        AtomicBoolean available = new AtomicBoolean(false);
        NetworkStatsProvider late = new NetworkStatsProvider() {
            @Override
            public boolean isAvailable() {
                return available.get();
            }

            @Override
            public int networkPlayerCount() {
                return 250;
            }

            @Override
            public int proxyCount() {
                return 4;
            }
        };

        FakeProxyPlatform platform = new FakeProxyPlatform();
        try (ExporterHarness harness = ExporterHarness.start(platform,
                builder -> builder.enable(MetricId.REDISBUNGEE), late, Clock.systemUTC())) {
            assertThat(platform.recordingLogger().warnedAbout("not available yet")).isTrue();
            assertThat(harness.scrape().hasSample(NS + "redis_players_online")).isFalse();

            available.set(true);

            assertThat(harness.scrape().value(NS + "redis_players_online")).contains(250.0d);
            assertThat(harness.scrape().value(NS + "redis_proxies_online")).contains(4.0d);
        }
    }

    /** A clock the test moves by hand, so uptime assertions are exact. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
