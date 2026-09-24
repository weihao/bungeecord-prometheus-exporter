package org.akadia.prometheus.core.metrics;

import org.akadia.prometheus.core.platform.BackendPing;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackendHealthMetricsTest {

    private static final String NS = "minecraft_proxy_";

    @Test
    @DisplayName("a responding backend reports up with its advertised player counts")
    void respondingBackend() {
        FakeBackendServer lobby = FakeBackendServer.named("lobby")
                .responding(new BackendPing(7, 100, 767, "1.21.4"));
        FakeProxyPlatform platform = new FakeProxyPlatform().addServer(lobby);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "server_up", "server", "lobby")).contains(1.0d);
            assertThat(scrape.value(NS + "server_reported_players", "server", "lobby")).contains(7.0d);
            assertThat(scrape.value(NS + "server_reported_max_players", "server", "lobby")).contains(100.0d);
            assertThat(scrape.value(NS + "server_ping_seconds", "server", "lobby")).isPresent();
        }
    }

    @Test
    @DisplayName("a refused connection reports down and suppresses the latency series")
    void failingBackend() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("dead").failing());

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "server_up", "server", "dead")).contains(0.0d);
            // A latency of 0 for an unreachable server would look like a perfect backend.
            assertThat(scrape.value(NS + "server_ping_seconds", "server", "dead")).isEmpty();
            assertThat(scrape.value(NS + "server_reported_players", "server", "dead")).isEmpty();
        }
    }

    @Test
    @DisplayName("a backend that throws synchronously does not abort the rest of the refresh")
    void synchronouslyThrowingBackend() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("broken").throwingSynchronously())
                .addServer(FakeBackendServer.named("healthy"));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "server_up", "server", "broken")).contains(0.0d);
            assertThat(scrape.value(NS + "server_up", "server", "healthy")).contains(1.0d);
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("a backend that never answers is bounded by the configured timeout")
    void hangingBackendIsTimedOut() {
        // The whole reason pings happen on a sampler rather than in the scrape callback:
        // without the timeout this refresh would never return.
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("hung").hanging())
                .addServer(FakeBackendServer.named("healthy"));

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .backendPingTimeout(Duration.ofMillis(300)))) {
            long start = System.nanoTime();
            harness.exporter().runSamplersNow();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "server_up", "server", "hung")).contains(0.0d);
            assertThat(scrape.value(NS + "server_up", "server", "healthy")).contains(1.0d);
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("pings are issued concurrently, so N slow backends cost one timeout not N")
    void pingsRunConcurrently() {
        FakeProxyPlatform platform = new FakeProxyPlatform();
        for (int i = 0; i < 6; i++) {
            platform.addServer(FakeBackendServer.named("hung" + i).hanging());
        }

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .backendPingTimeout(Duration.ofMillis(500)))) {
            long start = System.nanoTime();
            harness.exporter().runSamplersNow();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            // Serial timeouts would take at least 3 s for six backends.
            assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("an unregistered backend stops being reported")
    void unregisteredBackendsAreForgotten() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("temporary"))
                .addServer(FakeBackendServer.named("permanent"));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();
            assertThat(harness.scrape().value(NS + "server_up", "server", "temporary")).isPresent();

            platform.removeServer("temporary");
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "server_up", "server", "temporary")).isEmpty();
            assertThat(scrape.value(NS + "server_up", "server", "permanent")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("a newly registered backend starts being reported")
    void newBackendsAppear() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();

            platform.addServer(FakeBackendServer.named("newly-added"));
            harness.exporter().runSamplersNow();

            assertThat(harness.scrape().value(NS + "server_up", "server", "newly-added"))
                    .contains(1.0d);
        }
    }

    @Test
    @DisplayName("the scrape path never pings, so a dead backend cannot slow down /metrics")
    void scrapingDoesNotPing() {
        FakeBackendServer lobby = FakeBackendServer.named("lobby");
        FakeProxyPlatform platform = new FakeProxyPlatform().addServer(lobby);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();
            int afterSampler = lobby.pingCount();

            harness.scrape();
            harness.scrape();
            harness.scrape();

            assertThat(lobby.pingCount())
                    .as("scrapes must serve the cached sample, never issue new pings")
                    .isEqualTo(afterSampler);
        }
    }

    @Test
    @DisplayName("sampler runs and durations are recorded")
    void samplerSelfMetrics() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "exporter_sampler_runs_total",
                    "sampler", BackendHealthMetrics.SAMPLER_NAME, "result", "success"))
                    .hasValueSatisfying(value -> assertThat(value).isGreaterThanOrEqualTo(2.0d));
            assertThat(scrape.value(NS + "exporter_sampler_duration_seconds_count",
                    "sampler", BackendHealthMetrics.SAMPLER_NAME))
                    .hasValueSatisfying(value -> assertThat(value).isGreaterThanOrEqualTo(2.0d));
        }
    }

    @Test
    @DisplayName("a backend that reports no player counts omits those series")
    void unknownPlayerCountsAreOmitted() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("quiet").responding(BackendPing.empty()));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "server_up", "server", "quiet")).contains(1.0d);
            assertThat(scrape.value(NS + "server_reported_players", "server", "quiet")).isEmpty();
        }
    }
}
