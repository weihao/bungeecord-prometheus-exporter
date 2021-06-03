package org.akadia.prometheus.interfaces;

import net.md_5.bungee.api.plugin.Plugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;

public abstract class Metric implements Configurable {

    private final Plugin plugin;

    public Metric(Plugin plugin) {
        this.plugin = plugin;
    }

    public Plugin getPlugin() {
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
        final Logger log = this.getPlugin().getLogger();
        final String className = this.getClass().getSimpleName();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        log.warning(String.format("Failed to collect metric '%s': %s",
                className, sw.toString()));

        log.throwing(className, "collect", e);
    }

    public abstract String getConfigKey();

    public abstract String getHelp();

    public String[] getLabels() {
        return new String[]{};
    }

}
