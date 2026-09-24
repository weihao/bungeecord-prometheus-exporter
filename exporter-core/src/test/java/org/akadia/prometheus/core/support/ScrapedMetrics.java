package org.akadia.prometheus.core.support;

import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A scrape, rendered through the real exposition writer and parsed back.
 *
 * <p>Tests assert against this rather than against the metric objects, because the
 * exposition text is the actual contract with Prometheus: it is where {@code _total}
 * suffixes get appended, units get added to names, and label ordering is decided. An
 * assertion on a {@code Counter} field would pass while the emitted name was wrong.
 */
public final class ScrapedMetrics {

    private final String text;
    private final Map<String, String> help = new LinkedHashMap<>();
    private final Map<String, String> type = new LinkedHashMap<>();
    private final List<Sample> samples = new ArrayList<>();

    private ScrapedMetrics(String text) {
        this.text = text;
        parse(text);
    }

    /** Renders {@code registry} in the Prometheus text format and parses the result. */
    public static ScrapedMetrics from(PrometheusRegistry registry) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ExpositionFormats.init().getPrometheusTextFormatWriter().write(out, registry.scrape());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ScrapedMetrics(out.toString(StandardCharsets.UTF_8));
    }

    /** Renders {@code registry} in the OpenMetrics text format and parses the result. */
    public static ScrapedMetrics openMetricsFrom(PrometheusRegistry registry) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ExpositionFormats.init().getOpenMetricsTextFormatWriter().write(out, registry.scrape());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ScrapedMetrics(out.toString(StandardCharsets.UTF_8));
    }

    /** Parses an already-fetched exposition body, e.g. an HTTP response. */
    public static ScrapedMetrics parseText(String body) {
        return new ScrapedMetrics(body);
    }

    /** The raw exposition body. */
    public String text() {
        return text;
    }

    /** Every metric family name that carried a {@code # TYPE} line. */
    public Set<String> familyNames() {
        return new LinkedHashSet<>(type.keySet());
    }

    /** Every distinct sample name, including {@code _bucket}, {@code _sum} and {@code _count}. */
    public Set<String> sampleNames() {
        Set<String> names = new LinkedHashSet<>();
        samples.forEach(sample -> names.add(sample.name()));
        return names;
    }

    public Optional<String> typeOf(String familyName) {
        return Optional.ofNullable(type.get(familyName));
    }

    public Optional<String> helpOf(String familyName) {
        return Optional.ofNullable(help.get(familyName));
    }

    public List<Sample> samples() {
        return List.copyOf(samples);
    }

    /** Samples whose name matches exactly. */
    public List<Sample> samplesNamed(String name) {
        return samples.stream().filter(sample -> sample.name().equals(name)).toList();
    }

    public boolean hasSample(String name) {
        return samples.stream().anyMatch(sample -> sample.name().equals(name));
    }

    /**
     * Value of the single sample called {@code name} with exactly {@code labels}.
     *
     * @param labels alternating key and value, e.g. {@code "server", "lobby"}
     */
    public Optional<Double> value(String name, String... labels) {
        Map<String, String> wanted = toMap(labels);
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .filter(sample -> sample.labels().equals(wanted))
                .map(Sample::value)
                .findFirst();
    }

    /** Value of the only sample called {@code name}, failing the lookup if it is labelled. */
    public Optional<Double> value(String name) {
        return value(name, new String[0]);
    }

    private static Map<String, String> toMap(String... labels) {
        if (labels.length % 2 != 0) {
            throw new IllegalArgumentException("labels must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < labels.length; i += 2) {
            map.put(labels[i], labels[i + 1]);
        }
        return map;
    }

    private void parse(String body) {
        for (String rawLine : body.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.equals("# EOF")) {
                continue;
            }
            if (line.startsWith("# HELP ")) {
                String rest = line.substring("# HELP ".length());
                int space = rest.indexOf(' ');
                if (space > 0) {
                    help.put(rest.substring(0, space), rest.substring(space + 1));
                }
            } else if (line.startsWith("# TYPE ")) {
                String rest = line.substring("# TYPE ".length());
                int space = rest.indexOf(' ');
                if (space > 0) {
                    type.put(rest.substring(0, space), rest.substring(space + 1));
                }
            } else if (line.startsWith("# UNIT ") || line.startsWith("#")) {
                // UNIT lines and other comments carry no assertion value here.
                continue;
            } else {
                parseSample(line).ifPresent(samples::add);
            }
        }
    }

    private static Optional<Sample> parseSample(String line) {
        int brace = line.indexOf('{');
        String name;
        Map<String, String> labels = new LinkedHashMap<>();
        String remainder;

        if (brace < 0) {
            int space = line.indexOf(' ');
            if (space < 0) {
                return Optional.empty();
            }
            name = line.substring(0, space);
            remainder = line.substring(space + 1);
        } else {
            int closing = line.lastIndexOf('}');
            if (closing < brace) {
                return Optional.empty();
            }
            name = line.substring(0, brace);
            labels.putAll(parseLabels(line.substring(brace + 1, closing)));
            remainder = line.substring(closing + 1).strip();
        }

        // A trailing timestamp, if present, is not asserted on.
        String valueToken = remainder.split("\\s+")[0];
        try {
            return Optional.of(new Sample(name, labels, Double.parseDouble(valueToken)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Map<String, String> parseLabels(String labelBlock) {
        Map<String, String> labels = new LinkedHashMap<>();
        int index = 0;
        while (index < labelBlock.length()) {
            int equals = labelBlock.indexOf('=', index);
            if (equals < 0) {
                break;
            }
            String key = labelBlock.substring(index, equals).strip();
            int openQuote = labelBlock.indexOf('"', equals);
            if (openQuote < 0) {
                break;
            }
            StringBuilder value = new StringBuilder();
            int cursor = openQuote + 1;
            while (cursor < labelBlock.length() && labelBlock.charAt(cursor) != '"') {
                char c = labelBlock.charAt(cursor);
                if (c == '\\' && cursor + 1 < labelBlock.length()) {
                    cursor++;
                    char escaped = labelBlock.charAt(cursor);
                    value.append(switch (escaped) {
                        case 'n' -> '\n';
                        case '\\' -> '\\';
                        case '"' -> '"';
                        default -> escaped;
                    });
                } else {
                    value.append(c);
                }
                cursor++;
            }
            labels.put(key, value.toString());
            index = cursor + 1;
            while (index < labelBlock.length()
                    && (labelBlock.charAt(index) == ',' || labelBlock.charAt(index) == ' ')) {
                index++;
            }
        }
        return labels;
    }

    /** One exposition line. */
    public record Sample(String name, Map<String, String> labels, double value) {
    }
}
