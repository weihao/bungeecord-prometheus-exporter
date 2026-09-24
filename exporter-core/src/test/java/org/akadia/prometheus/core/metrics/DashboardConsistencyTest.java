package org.akadia.prometheus.core.metrics;

import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the shipped Grafana dashboard and the alerting examples honest.
 *
 * <p>A dashboard is the most common casualty of a metric rename: nothing fails, the panels
 * just quietly go blank, and it is usually a user who notices. These tests cross-check
 * every {@code minecraft_proxy_*} name referenced in the committed JSON and rules against
 * an actual scrape with every metric group enabled.
 */
class DashboardConsistencyTest {

    private static final Path DASHBOARD = Path.of("..", "dashboards", "proxy-overview.json");
    private static final Path ALERTS = Path.of("..", "examples", "prometheus", "alerts.yml");

    /** Matches a metric name, plus any histogram suffix a query may attach. */
    private static final Pattern METRIC_REFERENCE =
            Pattern.compile("minecraft_proxy_[a-z0-9_]+");

    @ParameterizedTest
    @ValueSource(strings = {"dashboard", "alerts"})
    @DisplayName("every referenced metric is one the exporter actually emits")
    void referencedMetricsExist(String which) throws IOException {
        Path file = which.equals("dashboard") ? DASHBOARD : ALERTS;
        assertThat(file).exists();

        Set<String> emitted = emittedSampleNames();
        Set<String> unknown = new TreeSet<>();

        Matcher matcher = METRIC_REFERENCE.matcher(
                Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
            String reference = matcher.group();
            if (!emitted.contains(reference) && !emitted.contains(stripSuffix(reference))) {
                unknown.add(reference);
            }
        }

        assertThat(unknown)
                .as("%s references metrics the exporter does not emit; either the name "
                        + "changed or the file is out of date", file.getFileName())
                .isEmpty();
    }

    @Test
    @DisplayName("the dashboard is valid JSON with a stable uid")
    void dashboardIsWellFormed() throws IOException {
        String json = Files.readString(DASHBOARD, StandardCharsets.UTF_8);

        // A changed uid makes Grafana import a second copy instead of updating the first.
        assertThat(json).contains("\"uid\": \"minecraft-proxy-overview\"");
        assertThat(json).contains("\"title\": \"Minecraft Proxy Overview\"");
        // Balanced braces is a cheap structural check without pulling in a JSON parser.
        assertThat(json.chars().filter(c -> c == '{').count())
                .isEqualTo(json.chars().filter(c -> c == '}').count());
    }

    @Test
    @DisplayName("the dashboard covers the metrics an operator needs first")
    void dashboardCoversTheEssentials() throws IOException {
        String json = Files.readString(DASHBOARD, StandardCharsets.UTF_8);

        assertThat(json)
                .contains("minecraft_proxy_players_online")
                .contains("minecraft_proxy_server_up")
                .contains("minecraft_proxy_player_ping_seconds_bucket")
                .contains("minecraft_proxy_exporter_scrape_errors_total");
    }

    @Test
    @DisplayName("latency panels use histogram_quantile rather than a raw bucket value")
    void latencyIsQueriedCorrectly() throws IOException {
        // Graphing a _bucket series directly is a classic mistake and produces a
        // meaningless line; the whole point of switching to a histogram was to make
        // histogram_quantile available.
        String json = Files.readString(DASHBOARD, StandardCharsets.UTF_8);

        int bucketReferences = countOccurrences(json, "player_ping_seconds_bucket");
        int quantileCalls = countOccurrences(json, "histogram_quantile");

        assertThat(bucketReferences).isPositive();
        assertThat(quantileCalls).isGreaterThanOrEqualTo(bucketReferences);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String stripSuffix(String name) {
        for (String suffix : new String[]{"_bucket", "_count", "_sum", "_total", "_created"}) {
            if (name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    /** Every sample name the exporter can emit, with all metric groups on. */
    private static Set<String> emittedSampleNames() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addPlayer("Example", "lobby", 42)
                .addPlugin("proxy-prometheus-exporter", "4.0.0");

        org.akadia.prometheus.core.platform.NetworkStatsProvider networkStats =
                new org.akadia.prometheus.core.platform.NetworkStatsProvider() {
                    @Override
                    public int networkPlayerCount() {
                        return 0;
                    }

                    @Override
                    public int proxyCount() {
                        return 0;
                    }
                };

        try (ExporterHarness harness = ExporterHarness.start(platform,
                builder -> builder.enabledMetrics(EnumSet.allOf(MetricId.class)),
                networkStats, java.time.Clock.systemUTC())) {

            EventMetrics events = harness.exporter().events();
            events.recordConnection();
            events.recordLogin(true);
            events.recordDisconnect();
            events.recordChat();
            events.recordCommand("/tp");
            events.recordKick("lobby");
            events.recordServerSwitch("lobby", "survival");
            events.recordListPing();
            harness.exporter().runSamplersNow();

            ScrapedMetrics scrape = harness.scrape();
            Set<String> names = new TreeSet<>(scrape.sampleNames());
            names.addAll(scrape.familyNames());
            // Error counters only materialise on failure, but a dashboard should still be
            // allowed to chart them.
            names.add("minecraft_proxy_exporter_scrape_errors_total");
            names.add("minecraft_proxy_exporter_series_dropped_total");
            return names;
        }
    }
}
