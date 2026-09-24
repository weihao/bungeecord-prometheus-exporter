package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the Prometheus naming conventions on every metric the exporter can emit.
 *
 * <p>This is the in-process equivalent of {@code promtool check metrics}, and it is the
 * test that justifies the breaking rename in 4.0. The 3.x exporter shipped
 * {@code bungeecord_player_connects} (a counter with no {@code _total}),
 * {@code bungeecord_jvm_memory} (bytes with no unit, and a namespace on a standard metric
 * name) and a latency Summary in milliseconds. Each of those is a rule below, so the same
 * class of mistake cannot come back unnoticed.
 *
 * <p>It runs with <em>every</em> metric group enabled, including the ones that default to
 * off, so nothing escapes review by being off in the default config.
 *
 * @see <a href="https://prometheus.io/docs/practices/naming/">Prometheus metric naming</a>
 */
class MetricNamingConventionTest {

    /** Metric names: snake_case ASCII, no double underscores, no uppercase. */
    private static final Pattern METRIC_NAME = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");

    /** Label names: same shape. Prometheus also allows uppercase, but mixing cases is a trap. */
    private static final Pattern LABEL_NAME = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");

    /**
     * Metric families owned by the Prometheus JVM instrumentation rather than by us. They
     * follow the cross-language standard, and prefixing them is exactly the bug being
     * guarded against, so they are checked for validity but exempt from the namespace rule.
     */
    private static final List<String> STANDARD_PREFIXES = List.of("jvm_", "process_");

    /** Suffixes that Prometheus treats as units; a metric using one must mean it. */
    private static final List<String> UNIT_SUFFIXES =
            List.of("_seconds", "_bytes", "_ratio", "_celsius", "_joules", "_meters", "_volts");

    private ExporterHarness harness;

    @BeforeEach
    void startExporterWithEveryMetricEnabled() {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addPlayer("Notch", "lobby", 42)
                .addPlayer("Herobrine", null, 130)
                .addServer(FakeBackendServer.named("lobby"))
                .addServer(FakeBackendServer.named("survival"))
                .addPlugin("proxy-prometheus-exporter", "4.0.0");

        harness = ExporterHarness.start(platform, builder -> builder
                .enabledMetrics(EnumSet.allOf(MetricId.class)));

        // Exercise every event path so the counters actually appear in the output.
        EventMetrics events = harness.exporter().events();
        events.recordConnection();
        events.recordLogin(true);
        events.recordLogin(false);
        events.recordDisconnect();
        events.recordChat();
        events.recordCommand("/tp Notch");
        events.recordKick("survival");
        events.recordServerSwitch("lobby", "survival");
        events.recordListPing();

        // Populate the sampled metrics so backend health and sampler stats are in scope.
        harness.exporter().runSamplersNow();
    }

    @AfterEach
    void stopExporter() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("every metric name is valid, lower snake_case, and free of double underscores")
    void metricNamesAreWellFormed() {
        Set<String> offenders = new TreeSet<>();
        for (String name : allSampleNames()) {
            if (!PrometheusNaming.isValidMetricName(name)
                    || !METRIC_NAME.matcher(name).matches()
                    || name.contains("__")) {
                offenders.add(name);
            }
        }
        assertThat(offenders)
                .as("metric names must match %s and be accepted by the Prometheus client",
                        METRIC_NAME.pattern())
                .isEmpty();
    }

    @Test
    @DisplayName("every counter ends in _total")
    void countersEndInTotal() {
        ScrapedMetrics scrape = harness.scrape();
        Set<String> offenders = new TreeSet<>();
        for (String family : scrape.familyNames()) {
            if (!"counter".equals(scrape.typeOf(family).orElse(""))) {
                continue;
            }
            // Depending on the writer, either the family name already carries the suffix
            // or the samples underneath it do. Either spelling is correct; neither is not.
            boolean suffixPresent = family.endsWith("_total")
                    || !scrape.samplesNamed(family + "_total").isEmpty();
            if (!suffixPresent) {
                offenders.add(family);
            }
        }
        assertThat(offenders)
                .as("counters must be suffixed _total so rate() reads correctly")
                .isEmpty();
    }

    @Test
    @DisplayName("nothing but a counter ends in _total")
    void onlyCountersEndInTotal() {
        ScrapedMetrics scrape = harness.scrape();
        Set<String> offenders = new TreeSet<>();
        scrape.familyNames().forEach(family -> {
            String type = scrape.typeOf(family).orElse("");
            if (family.endsWith("_total") && !"counter".equals(type)) {
                offenders.add(family + " (" + type + ")");
            }
        });
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("gauges and histograms do not use counter or info suffixes they have not earned")
    void unitSuffixesAreOnlyUsedForRealUnits() {
        // Anything named *_seconds must carry the SECONDS unit, which the client only
        // attaches when unit() was set. The OpenMetrics rendering exposes that as a
        // "# UNIT" line, so its absence on a _seconds metric means the unit was forgotten.
        String openMetrics = harness.scrapeOpenMetrics().text();
        Set<String> offenders = new TreeSet<>();
        for (String family : harness.scrapeOpenMetrics().familyNames()) {
            if (isStandard(family)) {
                continue;
            }
            for (String suffix : UNIT_SUFFIXES) {
                if (family.endsWith(suffix)
                        && !openMetrics.contains("# UNIT " + family + " " + suffix.substring(1))) {
                    offenders.add(family);
                }
            }
        }
        assertThat(offenders)
                .as("a metric named *_<unit> must declare that unit so the # UNIT line is emitted")
                .isEmpty();
    }

    @Test
    @DisplayName("latency is exported in seconds, never milliseconds")
    void latencyUsesBaseUnits() {
        Set<String> offenders = new TreeSet<>();
        for (String name : allSampleNames()) {
            if (name.contains("_millis") || name.contains("_ms") || name.endsWith("_milliseconds")
                    || name.contains("_micros") || name.contains("_nanos")) {
                offenders.add(name);
            }
        }
        assertThat(offenders)
                .as("Prometheus base unit for time is seconds")
                .isEmpty();
    }

    @Test
    @DisplayName("every metric we define is namespaced, and no standard metric is")
    void namespacingIsAppliedExactlyWhereItBelongs() {
        String namespace = harness.exporter().config().namespace() + "_";
        Set<String> missingNamespace = new TreeSet<>();
        Set<String> wronglyNamespaced = new TreeSet<>();

        for (String name : allSampleNames()) {
            boolean standard = isStandard(name);
            if (standard && name.startsWith(namespace)) {
                wronglyNamespaced.add(name);
            }
            if (!standard && !name.startsWith(namespace)) {
                missingNamespace.add(name);
            }
        }

        assertThat(missingNamespace)
                .as("exporter-defined metrics must carry the configured namespace")
                .isEmpty();
        assertThat(wronglyNamespaced)
                .as("jvm_* and process_* are cross-language standard names; prefixing them "
                        + "breaks every off-the-shelf JVM dashboard")
                .isEmpty();
    }

    @Test
    @DisplayName("standard JVM and process metrics are present and unprefixed")
    void standardJvmMetricsAreExported() {
        Set<String> names = allSampleNames();
        assertThat(names).anyMatch(name -> name.startsWith("jvm_memory_"));
        assertThat(names).anyMatch(name -> name.startsWith("process_"));
        assertThat(names).noneMatch(name -> name.contains("_jvm_memory"));
    }

    @Test
    @DisplayName("every label name is valid lower snake_case")
    void labelNamesAreWellFormed() {
        Set<String> offenders = new TreeSet<>();
        for (ScrapedMetrics.Sample sample : harness.scrape().samples()) {
            for (String label : sample.labels().keySet()) {
                // "le" and "quantile" are reserved bucket/quantile labels.
                if (label.equals("le") || label.equals("quantile")) {
                    continue;
                }
                if (!PrometheusNaming.isValidLabelName(label) || !LABEL_NAME.matcher(label).matches()) {
                    offenders.add(label + " on " + sample.name());
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("every metric family carries a non-trivial HELP string")
    void everyFamilyIsDocumented() {
        ScrapedMetrics scrape = harness.scrape();
        Set<String> offenders = new TreeSet<>();
        for (String family : scrape.familyNames()) {
            if (isStandard(family)) {
                continue;
            }
            String help = scrape.helpOf(family).orElse("");
            if (help.length() < 15) {
                offenders.add(family + " -> '" + help + "'");
            }
        }
        assertThat(offenders)
                .as("HELP is what an operator reads in the Prometheus UI; it must say something")
                .isEmpty();
    }

    @Test
    @DisplayName("no metric name collides with the reserved _info, _created or _bucket shapes")
    void reservedSuffixesAreNotAbused() {
        ScrapedMetrics scrape = harness.scrape();
        Set<String> offenders = new TreeSet<>();
        scrape.familyNames().forEach(family -> {
            String type = scrape.typeOf(family).orElse("");
            if (family.endsWith("_bucket") || family.endsWith("_count") || family.endsWith("_sum")) {
                offenders.add(family + " (" + type + ") collides with histogram sample suffixes");
            }
        });
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("MetricNames constants and the emitted output agree")
    void everyDeclaredNameIsActuallyEmitted() {
        Set<String> emitted = harness.scrape().familyNames();
        String namespace = harness.exporter().config().namespace() + "_";

        // Names that only appear once the corresponding condition occurs. A labelled counter
        // has no data points until something increments it, and emitting a zero series for
        // every possible collector name would be noise rather than signal.
        Set<String> conditional = Set.of(
                MetricNames.REDIS_PLAYERS_ONLINE,
                MetricNames.REDIS_PROXIES_ONLINE,
                MetricNames.EXPORTER_SCRAPE_ERRORS_TOTAL,
                MetricNames.EXPORTER_SERIES_DROPPED_TOTAL);

        Set<String> missing = new TreeSet<>();
        for (String suffix : declaredMetricSuffixes()) {
            if (conditional.contains(suffix)) {
                continue;
            }
            String full = namespace + suffix;
            // Counters lose their _total in the family name of the text format only when the
            // client re-adds it; accept either spelling.
            boolean present = emitted.contains(full)
                    || emitted.contains(stripTotal(full))
                    || harness.scrape().hasSample(full);
            if (!present) {
                missing.add(full);
            }
        }
        assertThat(missing)
                .as("MetricNames declares a constant that nothing emits; either wire it up or "
                        + "delete it so the documented metric list stays true")
                .isEmpty();
    }

    private static String stripTotal(String name) {
        return name.endsWith("_total") ? name.substring(0, name.length() - "_total".length()) : name;
    }

    private Set<String> allSampleNames() {
        return harness.scrape().sampleNames();
    }

    private static boolean isStandard(String name) {
        return STANDARD_PREFIXES.stream().anyMatch(name::startsWith);
    }

    /** Reads the public constants off {@link MetricNames} so the list cannot drift. */
    private static Set<String> declaredMetricSuffixes() {
        Set<String> names = new TreeSet<>();
        for (java.lang.reflect.Field field : MetricNames.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || field.getType() != String.class
                    || field.getName().startsWith("LABEL_")) {
                continue;
            }
            try {
                names.add((String) field.get(null));
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        return names;
    }
}
