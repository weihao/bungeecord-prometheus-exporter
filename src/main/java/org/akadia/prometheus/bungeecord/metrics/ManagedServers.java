package org.akadia.prometheus.bungeecord.metrics;

import org.akadia.prometheus.bungeecord.PrometheusBungeecordExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class ManagedServers extends GauageMetric {

    public ManagedServers(PrometheusBungeecordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().set(((PrometheusBungeecordExporter) getPlugin()).getProxy().getServers().size());
    }

    @Override
    public String getConfigKey() {
        return "managed_servers";
    }

    @Override
    public String getHelp() {
        return "the number of managed servers in Bungeecord";
    }
}
