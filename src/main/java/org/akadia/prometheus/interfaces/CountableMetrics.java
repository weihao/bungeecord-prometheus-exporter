package org.akadia.prometheus.interfaces;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.utils.Util;

public abstract class CountableMetrics implements Listener, Configurable {
    private final Plugin plugin;
    private final Counter counter;

    public CountableMetrics(Plugin plugin) {
        this.plugin = plugin;
        this.counter = Counter.build()
                .name(Util.prefix(this.getConfigKey()))
                .labelNames(this.getLabels())
                .help(this.getHelp())
                .register();
    }

    public Counter getCounter() {
        return counter;
    }

    public abstract String getConfigKey();

    public String[] getLabels() {
        return null;
    }

    ;
}
