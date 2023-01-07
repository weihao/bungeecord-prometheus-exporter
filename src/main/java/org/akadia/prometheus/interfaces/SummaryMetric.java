package org.akadia.prometheus.interfaces;

import io.prometheus.client.Summary;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.utils.Util;

public abstract class SummaryMetric extends Metric {

    private final Summary summary;

    public SummaryMetric(PrometheusExporter plugin) {
        super(plugin);

        this.summary = Summary.build()
                .name(Util.prefix(plugin.getPrefix(), this.getConfigKey()))
                .help(this.getHelp())
                .quantile(0.95, 0.005)  // 0.95 quantile with 0.005 allowed error
                .labelNames(this.getLabels())
                .create()
                .register();
    }

    public Summary getSummary() {
        return summary;
    }

}
