package org.akadia.prometheus.core.metrics;

import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A broken collector must cost one metric, not the whole target.
 *
 * <p>Proxy APIs throw in ways that are hard to predict: a server is unregistered while the
 * list is being iterated, a player disconnects between the count and the read, a
 * third-party plugin returns a null description. In the 3.x exporter any of those turned
 * {@code /metrics} into an HTTP 500, which Prometheus records as the entire target being
 * down - so one flaky metric took every other metric with it, including the JVM and
 * player-count series an operator actually pages on.
 */
class CollectorFailureContainmentTest {

    private static final String NS = "minecraft_proxy_";

    @Test
    @DisplayName("a throwing collector does not stop the rest of the scrape")
    void oneBrokenCollectorDoesNotBreakTheScrape() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addPlugin("some-plugin", "1.0.0")
                .failPlayersWith(() -> new IllegalStateException("player list exploded"));

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST))) {
            ScrapedMetrics scrape = harness.scrape();

            // The failing collector contributes nothing...
            assertThat(scrape.hasSample(NS + "player_online")).isFalse();
            // ...but everything else is still served.
            assertThat(scrape.value(NS + "servers_registered")).contains(1.0d);
            assertThat(scrape.value(NS + "plugins_installed")).contains(1.0d);
            assertThat(scrape.sampleNames()).anyMatch(name -> name.startsWith("jvm_"));
        }
    }

    @Test
    @DisplayName("the endpoint still answers 200 when a collector fails")
    void endpointStaysHealthy() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .failPlayersWith(() -> new RuntimeException("boom"));

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST))) {
            HttpResponse<String> response = harness.get("/metrics");

            assertThat(response.statusCode())
                    .as("a 500 would make Prometheus mark the whole proxy as down")
                    .isEqualTo(200);
            assertThat(response.body()).contains(NS + "servers_registered");
        }
    }

    @Test
    @DisplayName("the failure is counted so the gap in the data is visible, not silent")
    void failuresAreCounted() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .failPlayersWith(() -> new IllegalStateException("nope"));

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST))) {
            harness.scrape();
            harness.scrape();
            ScrapedMetrics scrape = harness.scrape();

            assertThat(scrape.value(NS + "exporter_scrape_errors_total",
                    "collector", MetricNames.PLAYER_ONLINE))
                    .hasValueSatisfying(value -> assertThat(value).isGreaterThanOrEqualTo(2.0d));
        }
    }

    @Test
    @DisplayName("the stack trace is logged once, not once per scrape")
    void failuresAreLoggedOnce() {
        // Prometheus scrapes every few seconds; a stack trace per scrape fills the proxy
        // log and hides everything else.
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .failPlayersWith(() -> new IllegalStateException("nope"));

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST))) {
            for (int i = 0; i < 25; i++) {
                harness.scrape();
            }

            // Two collectors read the player list, so two of them fail; each logs exactly
            // once no matter how many scrapes happen.
            assertThat(platform.recordingLogger().errors()).hasSize(2);
            assertThat(platform.recordingLogger().erroredAbout(MetricNames.PLAYER_ONLINE)).isTrue();
            assertThat(platform.recordingLogger()
                    .erroredAbout(MetricNames.SERVER_PLAYERS_ONLINE)).isTrue();
        }
    }

    @Test
    @DisplayName("an Error, not just an Exception, is contained too")
    void errorsAreContained() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"));
        // A relocated-dependency mistake surfaces as NoClassDefFoundError at collect time,
        // which is an Error rather than an Exception.
        platform.failPlayersWith(() -> {
            throw new NoClassDefFoundError("some/relocated/Class");
        });

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST))) {
            assertThat(harness.get("/metrics").statusCode()).isEqualTo(200);
            assertThat(harness.scrape().value(NS + "servers_registered")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("a failing sampler keeps its schedule instead of being cancelled")
    void samplerSurvivesFailure() {
        // ScheduledExecutorService silently cancels a task that throws, which would leave
        // the exporter serving stale backend health forever with no signal.
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .failPlayersWith(() -> new IllegalStateException("player list exploded"));

        try (ExporterHarness harness = ExporterHarness.start(platform)) {
            harness.exporter().runSamplersNow();
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            // The player-ping sampler failed both times...
            assertThat(scrape.value(NS + "exporter_sampler_runs_total",
                    "sampler", PlayerPingMetrics.SAMPLER_NAME, "result", "failure"))
                    .contains(2.0d);
            // ...while the backend sampler carried on unaffected.
            assertThat(scrape.value(NS + "server_up", "server", "lobby")).contains(1.0d);
        }
    }
}
