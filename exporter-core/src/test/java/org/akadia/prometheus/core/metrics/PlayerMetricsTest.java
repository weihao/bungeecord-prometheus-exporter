package org.akadia.prometheus.core.metrics;

import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerMetricsTest {

    private static final String NS = "minecraft_proxy_";

    @Test
    @DisplayName("the online count comes from the proxy's own counter")
    void playersOnline() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addPlayer("Notch", "lobby", 20)
                .addPlayer("Herobrine", "survival", 80);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            assertThat(harness.scrape().value(NS + "players_online")).contains(2.0d);

            platform.addPlayer("Steve", "lobby", 30);

            // A callback gauge re-reads on every scrape, so no refresh step is needed.
            assertThat(harness.scrape().value(NS + "players_online")).contains(3.0d);
        }
    }

    @Test
    @DisplayName("per-server counts are derived from the player list so they always sum correctly")
    void playersPerServer() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addServer(FakeBackendServer.named("survival"))
                .addPlayer("a", "lobby", 20)
                .addPlayer("b", "lobby", 20)
                .addPlayer("c", "survival", 20);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            ScrapedMetrics scrape = harness.scrape();

            assertThat(scrape.value(NS + "server_players_online", "server", "lobby")).contains(2.0d);
            assertThat(scrape.value(NS + "server_players_online", "server", "survival")).contains(1.0d);

            double sum = scrape.samplesNamed(NS + "server_players_online").stream()
                    .mapToDouble(ScrapedMetrics.Sample::value).sum();
            assertThat(sum).isEqualTo(scrape.value(NS + "players_online").orElseThrow());
        }
    }

    @Test
    @DisplayName("an empty server reports zero instead of disappearing from the output")
    void emptyServersReportZero() {
        // A series that vanishes graphs as a gap, which reads as a broken scrape rather
        // than as an empty server.
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addServer(FakeBackendServer.named("ghost-town"))
                .addPlayer("a", "lobby", 20);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            assertThat(harness.scrape().value(NS + "server_players_online", "server", "ghost-town"))
                    .contains(0.0d);
        }
    }

    @Test
    @DisplayName("a player between servers is counted under 'none', not dropped")
    void playersWithoutAServer() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addPlayer("connecting", null, 15);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            ScrapedMetrics scrape = harness.scrape();

            assertThat(scrape.value(NS + "server_players_online", "server", "none")).contains(1.0d);
            assertThat(scrape.value(NS + "players_online")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("per-player metrics are absent by default")
    void perPlayerMetricsOffByDefault() {
        FakeProxyPlatform platform = new FakeProxyPlatform().addPlayer("Notch", "lobby", 20);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            assertThat(harness.scrape().sampleNames())
                    .doesNotContain(NS + "player_online", NS + "player_latency_seconds");
        }
    }

    @Test
    @DisplayName("per-player metrics carry name, uuid and server when enabled")
    void perPlayerMetricsWhenEnabled() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addPlayer("Notch", "lobby", 42)
                .addPlayer("Herobrine", null, 130);

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST)
                .enable(MetricId.PLAYER_PING_PER_PLAYER))) {
            ScrapedMetrics scrape = harness.scrape();

            assertThat(scrape.samplesNamed(NS + "player_online")).hasSize(2);
            ScrapedMetrics.Sample notch = scrape.samplesNamed(NS + "player_online").stream()
                    .filter(sample -> "Notch".equals(sample.labels().get("player")))
                    .findFirst().orElseThrow();
            assertThat(notch.value()).isEqualTo(1.0d);
            assertThat(notch.labels()).containsEntry("server", "lobby").containsKey("uuid");

            // Latency is reported in seconds, not the milliseconds the proxy hands us.
            assertThat(scrape.samplesNamed(NS + "player_latency_seconds").stream()
                    .filter(sample -> "Notch".equals(sample.labels().get("player")))
                    .findFirst().orElseThrow().value())
                    .isEqualTo(0.042d);
        }
    }

    @Test
    @DisplayName("a player with unknown latency is skipped rather than reported as zero")
    void unknownLatencyIsOmitted() {
        // BungeeCord returns -1 before the first keep-alive round trip. Reporting that as
        // 0 s would drag every latency percentile down.
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addPlayer("Fresh", "lobby", -1)
                .addPlayer("Settled", "lobby", 55);

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_PING_PER_PLAYER))) {
            assertThat(harness.scrape().samplesNamed(NS + "player_latency_seconds")).hasSize(1);
        }
    }

    @Test
    @DisplayName("per-player series stop at the cardinality budget")
    void perPlayerSeriesAreCapped() {
        FakeProxyPlatform platform = new FakeProxyPlatform().addPlayers(500, "lobby", 30);

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST)
                .maxSeriesPerMetric(50))) {
            // Two scrapes: drops happen while the player gauge's callback runs, and the
            // drop counter may have been collected earlier in that same pass, so it lags
            // by exactly one scrape. Prometheus scrapes repeatedly, so that is fine in
            // production - but the test has to account for it rather than hide it.
            harness.scrape();
            ScrapedMetrics scrape = harness.scrape();

            assertThat(scrape.samplesNamed(NS + "player_online")).hasSize(50);
            assertThat(scrape.value(NS + "exporter_series_dropped_total",
                    "metric", NS + "player_online"))
                    .hasValueSatisfying(value -> assertThat(value).isGreaterThan(400.0d));
            assertThat(platform.recordingLogger().warnedAbout("cardinality budget")).isTrue();
            // The aggregate is unaffected by the cap on the detailed metric.
            assertThat(scrape.value(NS + "players_online")).contains(500.0d);
        }
    }

    @Test
    @DisplayName("the latency histogram buckets sampled player pings")
    void latencyHistogram() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addPlayer("fast", "lobby", 20)
                .addPlayer("medium", "lobby", 90)
                .addPlayer("slow", "lobby", 400);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "player_ping_seconds_count")).contains(3.0d);
            assertThat(scrape.value(NS + "player_ping_seconds_sum")).contains(0.51d);
            assertThat(scrape.value(NS + "player_ping_seconds_bucket", "le", "0.025")).contains(1.0d);
            assertThat(scrape.value(NS + "player_ping_seconds_bucket", "le", "0.1")).contains(2.0d);
            assertThat(scrape.value(NS + "player_ping_seconds_bucket", "le", "0.5")).contains(3.0d);
        }
    }

    @Test
    @DisplayName("the histogram has no player label, so its cardinality is fixed")
    void histogramIsNotLabelledByPlayer() {
        FakeProxyPlatform platform = new FakeProxyPlatform().addPlayers(1000, "lobby", 30);

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();

            assertThat(harness.scrape().samplesNamed(NS + "player_ping_seconds_bucket"))
                    .allSatisfy(sample -> assertThat(sample.labels()).containsOnlyKeys("le"));
        }
    }
}
