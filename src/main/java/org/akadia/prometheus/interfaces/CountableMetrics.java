package org.akadia.prometheus.interfaces;

import io.prometheus.client.Counter;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.utils.Util;

public abstract class CountableMetrics extends Metric {
    private final Counter counter;

    public CountableMetrics(PrometheusExporter plugin) {
        super(plugin);

        this.counter = Counter.build()
                .name(Util.prefix(plugin.getPrefix(), this.getConfigKey()))
                .labelNames(this.getLabels())
                .help(this.getHelp())
                .create()
                .register();
    }

    @Override
    public void doCollect() {
    }

    public Counter getCounter() {
        return counter;
    }

    public abstract String getConfigKey();

    public abstract String getHelp();

}
