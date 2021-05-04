package org.akadia.prometheus;

import org.akadia.prometheus.interfaces.Metric;

import java.util.ArrayList;
import java.util.List;

public class MetricRegistry {
    private static final MetricRegistry INSTANCE = new MetricRegistry();

    private final List<Metric> metrics = new ArrayList<>();

    private MetricRegistry() {

    }

    public static MetricRegistry getInstance() {
        return INSTANCE;
    }

    public void register(Metric metric) {
        this.metrics.add(metric);
    }

    void collectMetrics() {
        this.metrics.forEach(Metric::collect);
    }

}
