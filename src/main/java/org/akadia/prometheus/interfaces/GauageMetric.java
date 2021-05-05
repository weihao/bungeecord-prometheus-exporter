package org.akadia.prometheus.interfaces;

import io.prometheus.client.Gauge;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.utils.Util;

public abstract class GauageMetric extends Metric {

    private final Gauge gauge;

    public GauageMetric(Plugin plugin) {
        super(plugin);

        this.gauge = Gauge.build()
                .name(Util.prefix(this.getConfigKey()))
                .help(this.getHelp())
                .labelNames(this.getLabels())
                .create()
                .register();
    }

    public Gauge getGauge() {
        return gauge;
    }

}
