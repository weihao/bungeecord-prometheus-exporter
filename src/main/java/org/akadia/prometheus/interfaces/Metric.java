package org.akadia.prometheus.interfaces;

import net.md_5.bungee.api.plugin.Plugin;

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

        log.warning(String.format("Failed to collect metric '%s' (see FINER log for stacktrace): %s",
                className, e.toString()));
        log.throwing(className, "collect", e);
    }

    public abstract String getConfigKey();

    public abstract String getHelp();

    public String[] getLabels() {
        return new String[]{};
    }

}
