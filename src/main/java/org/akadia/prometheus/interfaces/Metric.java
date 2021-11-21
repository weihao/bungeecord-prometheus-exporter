package org.akadia.prometheus.interfaces;

import org.akadia.prometheus.PrometheusExporter;

import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class Metric implements Configurable {

    private final PrometheusExporter plugin;

    public Metric(PrometheusExporter plugin) {
        this.plugin = plugin;
    }

    public PrometheusExporter getPlugin() {
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
        final String className = this.getClass().getSimpleName();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        plugin.warn(String.format("Failed to collect metric '%s': %s",
                className, sw));

        plugin.warn(className + " collect:" + e);
    }

    public abstract String getConfigKey();

    public abstract String getHelp();

    public String[] getLabels() {
        return new String[]{};
    }

}
