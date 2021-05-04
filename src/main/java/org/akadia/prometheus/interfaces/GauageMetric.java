package org.akadia.prometheus.interfaces;

import io.prometheus.client.Gauge;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.utils.Util;

import java.util.logging.Logger;

public abstract class GauageMetric implements Configurable {

    private final Plugin plugin;
    private final Gauge gauge;

    public GauageMetric(Plugin plugin) {
        this.plugin = plugin;

        this.gauge = Gauge.build()
                .name(Util.prefix(this.getConfigKey()))
                .help(this.getHelp())
                .labelNames(this.getLabels())
                .create();
    }

    public abstract String getHelp();

    public abstract String getConfigKey();

    public Gauge getGauge() {
        return gauge;
    }

    protected Plugin getPlugin() {
        return plugin;
    }

    public void collect() {
        try {
            doCollect();
        } catch (Exception e) {
            logException(e);
        }
    }

    public abstract void doCollect();

    private void logException(Exception e) {
        final Logger log = plugin.getLogger();
        final String className = getClass().getSimpleName();

        log.warning(String.format("Failed to collect metric '%s' (see FINER log for stacktrace): %s",
                className, e.toString()));
        log.throwing(className, "collect", e);
    }

    public String[] getLabels() {
        return new String[]{};
    }

}
