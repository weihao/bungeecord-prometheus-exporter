package org.akadia.prometheus.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.Metric;
import org.akadia.prometheus.utils.Util;

import java.util.List;

public class JvmGarbageCollectorWrapper extends Metric {

    public JvmGarbageCollectorWrapper(Plugin plugin) {
        super(plugin);
    }

    @Override
    protected void doCollect() {
    }

    @Override
    public String getConfigKey() {
        return "jvm_gc";
    }

    @Override
    public String getHelp() {
        return null;
    }

    private static class GarbageCollectorExportsCollector extends Collector {
        private static final GarbageCollectorExports garbageCollectorExports = new GarbageCollectorExports();

        @Override
        public List<MetricFamilySamples> collect() {
            return Util.prefixFromCollector(garbageCollectorExports);
        }
    }
}