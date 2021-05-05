package org.akadia.prometheus.interfaces;

import io.prometheus.client.Collector;
import net.md_5.bungee.api.plugin.Plugin;

public abstract class MetricWrapper extends Metric {
    private final Collector collector;

    public MetricWrapper(Plugin plugin, Collector collector) {
        super(plugin);
        this.collector = collector;
    }

    public Collector getCollector() {
        return collector;
    }

    public abstract String getConfigKey();

    public abstract String getHelp();
}
