package org.akadia.prometheus.bungeecord.metrics;

import org.akadia.prometheus.bungeecord.PrometheusBungeeCordExporter;
import org.akadia.prometheus.interfaces.GauageMetric;

public class InstalledNetworkPlugins extends GauageMetric {

    public InstalledNetworkPlugins(PrometheusBungeeCordExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge()
                .set(
                        ((PrometheusBungeeCordExporter) getPlugin())
                                .getProxy()
                                .getPluginManager()
                                .getPlugins().size()
                );
    }

    @Override
    public String getConfigKey() {
        return "installed_network_plugins";
    }

    @Override
    public String getHelp() {
        return "the number of installed network plugins in BungeeCord";
    }
}
