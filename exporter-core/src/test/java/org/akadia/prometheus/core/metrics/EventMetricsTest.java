package org.akadia.prometheus.core.metrics;

import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class EventMetricsTest {

    private static final String NS = "minecraft_proxy_";

    @Test
    @DisplayName("login outcomes are split by result and both series exist from the start")
    void loginOutcomes() {
        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform())) {
            EventMetrics events = harness.exporter().events();

            // Before anything happens, both label values must already be present so that
            // rate() has a baseline and a first denial is not mistaken for a counter reset.
            ScrapedMetrics initial = harness.scrape();
            assertThat(initial.value(NS + "logins_total", "result", "allowed")).contains(0.0d);
            assertThat(initial.value(NS + "logins_total", "result", "denied")).contains(0.0d);

            events.recordLogin(true);
            events.recordLogin(true);
            events.recordLogin(false);

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "logins_total", "result", "allowed")).contains(2.0d);
            assertThat(scrape.value(NS + "logins_total", "result", "denied")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("unlabelled event counters increment")
    void simpleCounters() {
        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform())) {
            EventMetrics events = harness.exporter().events();
            events.recordConnection();
            events.recordConnection();
            events.recordDisconnect();
            events.recordChat();
            events.recordListPing();

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "connections_total")).contains(2.0d);
            assertThat(scrape.value(NS + "disconnects_total")).contains(1.0d);
            assertThat(scrape.value(NS + "chat_messages_total")).contains(1.0d);
            assertThat(scrape.value(NS + "list_pings_total")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("kicks are labelled by the server the player was kicked from")
    void kicksAreLabelledByServer() {
        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform())) {
            EventMetrics events = harness.exporter().events();
            events.recordKick("survival");
            events.recordKick("survival");
            events.recordKick(null);

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "kicks_total", "server", "survival")).contains(2.0d);
            assertThat(scrape.value(NS + "kicks_total", "server", "unknown")).contains(1.0d);
        }
    }

    @Test
    @DisplayName("a server switch records both ends, with the initial connect as 'unknown'")
    void serverSwitches() {
        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform())) {
            EventMetrics events = harness.exporter().events();
            events.recordServerSwitch(null, "lobby");
            events.recordServerSwitch("lobby", "survival");

            ScrapedMetrics scrape = harness.scrape();
            assertThat(scrape.value(NS + "server_switches_total", "from", "unknown", "to", "lobby"))
                    .contains(1.0d);
            assertThat(scrape.value(NS + "server_switches_total", "from", "lobby", "to", "survival"))
                    .contains(1.0d);
        }
    }

    @Nested
    @DisplayName("commands")
    class Commands {

        @Test
        @DisplayName("without the command label there is a single unlabelled counter")
        void unlabelledByDefault() {
            try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform())) {
                harness.exporter().events().recordCommand("/tp Notch");
                harness.exporter().events().recordCommand("/gamemode creative");

                ScrapedMetrics scrape = harness.scrape();
                assertThat(scrape.value(NS + "commands_total")).contains(2.0d);
            }
        }

        @Test
        @DisplayName("with the command label enabled, the root word becomes the label")
        void labelledWhenEnabled() {
            try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform(),
                    builder -> builder.enable(MetricId.COMMAND_LABEL))) {
                harness.exporter().events().recordCommand("/tp Notch 1 2 3");
                harness.exporter().events().recordCommand("/tp Herobrine");
                harness.exporter().events().recordCommand("/gamemode creative");

                ScrapedMetrics scrape = harness.scrape();
                assertThat(scrape.value(NS + "commands_total", "command", "tp")).contains(2.0d);
                assertThat(scrape.value(NS + "commands_total", "command", "gamemode")).contains(1.0d);
            }
        }

        @Test
        @DisplayName("command input past the budget collapses into 'other' rather than exploding")
        void unboundedCommandsCollapse() {
            // Commands are typed by players, so the label is attacker-controlled. Left
            // unbounded, a scripted client could mint a new time series per keystroke.
            try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform(),
                    builder -> builder.enable(MetricId.COMMAND_LABEL).maxSeriesPerMetric(5))) {
                for (int i = 0; i < 200; i++) {
                    harness.exporter().events().recordCommand("/spam" + i);
                }

                ScrapedMetrics scrape = harness.scrape();
                long commandSeries = scrape.samplesNamed(NS + "commands_total").size();
                assertThat(commandSeries).isLessThanOrEqualTo(6);
                assertThat(scrape.value(NS + "commands_total", "command", "other"))
                        .hasValueSatisfying(value -> assertThat(value).isGreaterThan(100.0d));
                assertThat(scrape.value(NS + "exporter_series_dropped_total",
                        "metric", NS + "commands_total"))
                        .hasValueSatisfying(value -> assertThat(value).isGreaterThan(100.0d));
            }
        }

        @ParameterizedTest
        @CsvSource({
                "'/tp Notch',            tp",
                "'tp Notch',             tp",
                "'/TP',                  tp",
                "'  /tp  ',              tp",
                "'/minecraft:tp Notch',  tp",
                "'/say hello world',     say",
                "'/weird!!!name',        weirdname",
                "'/',                    unknown",
                "'',                     unknown"
        })
        @DisplayName("the command root is sanitised into a stable label value")
        void commandRootSanitisation(String raw, String expected) {
            assertThat(EventMetrics.commandRoot(raw)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a null command does not blow up")
        void nullCommand() {
            assertThat(EventMetrics.commandRoot(null)).isEqualTo("unknown");
        }

        @Test
        @DisplayName("an absurdly long command is truncated")
        void longCommandIsTruncated() {
            String root = EventMetrics.commandRoot("/" + "a".repeat(5000));

            assertThat(root).hasSize(32);
        }
    }

    @Test
    @DisplayName("with events disabled, nothing is registered and recording is a safe no-op")
    void eventsCanBeDisabled() {
        try (ExporterHarness harness = ExporterHarness.start(new FakeProxyPlatform(),
                builder -> builder.enabledMetrics(EnumSet.noneOf(MetricId.class)))) {
            EventMetrics events = harness.exporter().events();

            assertThat(events.isEnabled()).isFalse();
            // Adapters call these unconditionally; they must not NPE when the group is off.
            events.recordConnection();
            events.recordLogin(true);
            events.recordDisconnect();
            events.recordChat();
            events.recordCommand("/tp");
            events.recordKick("lobby");
            events.recordServerSwitch("a", "b");
            events.recordListPing();

            assertThat(harness.scrape().sampleNames())
                    .noneMatch(name -> name.contains("logins_total"));
        }
    }
}
