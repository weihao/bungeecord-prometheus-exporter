package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.snapshots.Unit;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.PlayerSnapshot;
import org.akadia.prometheus.core.platform.ProxyPlatform;

/**
 * Distribution of player latency across the whole network.
 *
 * <p>A histogram rather than a per-player gauge: it answers "what does the 95th percentile
 * player experience" in a single series per bucket, no matter how many players connect.
 * The previous exporter used a Summary labelled by player name, which produced one
 * quantile stream per player and could not be aggregated across proxies at all - Summary
 * quantiles are mathematically not averageable, whereas
 * {@code histogram_quantile(0.95, sum by (le) (rate(..._bucket[5m])))} is exactly right.
 *
 * <p>Latency is observed on an interval rather than at scrape time so that the buckets
 * reflect a steady sampling rate instead of however often Prometheus happens to ask.
 */
public final class PlayerPingMetrics {

    /** Sampler name used in logs and {@code exporter_sampler_runs_total}. */
    public static final String SAMPLER_NAME = "player-ping";

    /**
     * Bucket boundaries in seconds, chosen for Minecraft round-trip times: sub-25 ms is a
     * LAN or same-region player, 250 ms is intercontinental, past 1 s the client is
     * visibly rubber-banding.
     */
    static final double[] BUCKETS_SECONDS = {
            0.010, 0.025, 0.050, 0.075, 0.100, 0.150, 0.200, 0.300, 0.500, 1.000, 2.000
    };

    private final ProxyPlatform platform;
    private final Histogram histogram;

    public PlayerPingMetrics(ExporterConfig config,
                             ProxyPlatform platform,
                             MetricRegistrar registrar) {
        this.platform = platform;
        if (!config.isEnabled(MetricId.PLAYER_PING_HISTOGRAM)) {
            this.histogram = null;
            return;
        }
        this.histogram = registrar.register(MetricNames.PLAYER_PING_SECONDS, Histogram.builder()
                .name(config.metricName(MetricNames.PLAYER_PING_SECONDS))
                .unit(Unit.SECONDS)
                .help("Distribution of player round-trip latency, sampled on an interval")
                .classicUpperBounds(BUCKETS_SECONDS)
                .build());
    }

    public boolean isEnabled() {
        return histogram != null;
    }

    /** Observes the latency of every connected player exactly once. */
    public void sample() {
        if (histogram == null) {
            return;
        }
        for (PlayerSnapshot player : platform.players()) {
            if (player.hasKnownPing()) {
                histogram.observe(player.pingSeconds());
            }
        }
    }
}
