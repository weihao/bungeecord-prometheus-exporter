package org.akadia.prometheus.core.metrics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps how many distinct label combinations a single metric may emit.
 *
 * <p>An exporter that hands Prometheus an unbounded number of time series takes the
 * monitoring system down with it, and a Minecraft proxy is a perfect source of unbounded
 * labels: player names, command strings, server pairs. Rather than trusting operators to
 * get {@code metrics.player-list} right, every collector that emits a variable number of
 * series routes its label sets through here.
 *
 * <p>Once a metric reaches its limit, further <em>new</em> combinations are dropped and
 * counted; combinations already admitted keep reporting so the series stay continuous.
 *
 * <p>Instances are shared across the scrape thread and the sampler thread.
 */
public final class CardinalityGuard {

    /**
     * Notified whenever a label combination is refused.
     *
     * <p>{@code firstTime} is computed by the guard rather than by the listener so that a
     * caller can log once and count always without keeping its own bookkeeping - and
     * without needing a reference to the guard it is being constructed with.
     */
    @FunctionalInterface
    public interface DropListener {
        void onDropped(String metric, long count, boolean firstTime);

        DropListener NOOP = (metric, count, firstTime) -> { };
    }

    private final int maxSeriesPerMetric;
    private final DropListener dropListener;
    private final ConcurrentHashMap<String, Set<String>> admitted = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> warned = new ConcurrentHashMap<>();

    /**
     * @param maxSeriesPerMetric maximum label combinations retained per metric
     * @param dropListener       notified for every refused combination
     */
    public CardinalityGuard(int maxSeriesPerMetric, DropListener dropListener) {
        if (maxSeriesPerMetric < 1) {
            throw new IllegalArgumentException("maxSeriesPerMetric must be at least 1");
        }
        this.maxSeriesPerMetric = maxSeriesPerMetric;
        this.dropListener = dropListener == null ? DropListener.NOOP : dropListener;
    }

    /**
     * Decides whether {@code labelValues} may be emitted for {@code metric}.
     *
     * @return {@code true} when the combination is within budget
     */
    public boolean admit(String metric, String... labelValues) {
        Set<String> seen = admitted.computeIfAbsent(metric, key -> ConcurrentHashMap.newKeySet());
        String key = key(labelValues);
        if (seen.contains(key)) {
            return true;
        }
        // Racing threads may briefly overshoot the limit by the number of racers; the
        // overshoot is bounded and self-correcting, which is cheaper than locking the
        // scrape path.
        if (seen.size() >= maxSeriesPerMetric) {
            dropListener.onDropped(metric, 1, warned.putIfAbsent(metric, Boolean.TRUE) == null);
            return false;
        }
        seen.add(key);
        return true;
    }

    /**
     * Encodes a label tuple into one unambiguous key.
     *
     * <p>Length-prefixed rather than joined with a separator. Server names come from the
     * proxy's own configuration and may contain any character, so any separator is a
     * character that some server name can legitimately contain. With a plain join,
     * {@code {from="a b", to="c"}} and {@code {from="a", to="b c"}} collapse to the same
     * key and one of the two series is silently dropped as a duplicate.
     */
    private static String key(String... labelValues) {
        StringBuilder key = new StringBuilder(labelValues.length * 16);
        for (String value : labelValues) {
            String text = value == null ? "" : value;
            key.append(text.length()).append(':').append(text);
        }
        return key.toString();
    }

    /** Series currently admitted for {@code metric}. */
    public int admittedCount(String metric) {
        Set<String> seen = admitted.get(metric);
        return seen == null ? 0 : seen.size();
    }

    public int maxSeriesPerMetric() {
        return maxSeriesPerMetric;
    }

    /**
     * Forgets every admitted combination.
     *
     * <p>Called when the exporter shuts down. Not called between scrapes on purpose: the
     * budget is a lifetime budget, otherwise a churning label set would slip past it.
     */
    public void reset() {
        admitted.clear();
        warned.clear();
    }
}
