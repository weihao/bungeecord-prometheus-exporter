package org.akadia.prometheus.metrics;

import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.GauageMetric;

public class JvmMemory extends GauageMetric {


    public JvmMemory(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().labels("max").set(Runtime.getRuntime().maxMemory());
        this.getGauge().labels("free").set(Runtime.getRuntime().freeMemory());
        this.getGauge().labels("allocated").set(Runtime.getRuntime().totalMemory());
        this.getGauge().labels("used").set(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    @Override
    public String getConfigKey() {
        return "jvm_memory";
    }

    @Override
    public String getHelp() {
        return "JVM memory usage";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"type"};
    }
}
