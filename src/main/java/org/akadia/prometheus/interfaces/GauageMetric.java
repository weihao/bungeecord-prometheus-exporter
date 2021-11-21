package org.akadia.prometheus.interfaces;

import io.prometheus.client.Gauge;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.utils.Util;

public abstract class GauageMetric extends Metric {

    private final Gauge gauge;

    public GauageMetric(PrometheusExporter plugin) {
        super(plugin);

        this.gauge = Gauge.build()
                .name(Util.prefix(plugin.getPrefix(), this.getConfigKey()))
                .help(this.getHelp())
                .labelNames(this.getLabels())
                .create()
                .register();
    }

    public Gauge getGauge() {
        return gauge;
    }

}
