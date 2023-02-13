package org.akadia.prometheus.bungeecord.metrics;

import org.akadia.prometheus.bungeecord.PrometheusBungeeCordExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class ManagedServers extends GauageMetric {

    public ManagedServers(PrometheusBungeeCordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge()
                .set(
                        ((PrometheusBungeeCordExporter) getPlugin())
                                .getProxy()
                                .getServers()
                                .size()
                );
    }

    @Override
    public String getConfigKey() {
        return "managed_servers";
    }

    @Override
    public String getHelp() {
        return "the number of managed servers in BungeeCord";
    }
}
