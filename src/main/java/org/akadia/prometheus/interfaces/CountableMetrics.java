package org.akadia.prometheus.interfaces;

import io.prometheus.client.Counter;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.utils.Util;

public abstract class CountableMetrics extends Metric implements Listener {
    private final Counter counter;

    public CountableMetrics(Plugin plugin) {
        super(plugin);

        this.counter = Counter.build()
                .name(Util.prefix(this.getConfigKey()))
                .labelNames(this.getLabels())
                .help(this.getHelp())
                .create()
                .register();
    }

    @Override
    public void doCollect() {
    }

    public Counter getCounter() {
        return counter;
    }

    public abstract String getConfigKey();

    public abstract String getHelp();

}
