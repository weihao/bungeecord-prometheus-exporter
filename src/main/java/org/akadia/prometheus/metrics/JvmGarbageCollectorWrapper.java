package org.akadia.prometheus.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.MetricWrapper;
import org.akadia.prometheus.utils.Util;

import java.util.List;

public class JvmGarbageCollectorWrapper extends MetricWrapper {

    public JvmGarbageCollectorWrapper(PrometheusExporter plugin) {
        super(plugin, new GarbageCollectorExportsCollector(plugin.getPrefix()));
    }

    @Override
    public void doCollect() {
    }

    @Override
    public String getConfigKey() {
        return "jvm_gc";
    }

    @Override
    public String getHelp() {
        return "JVM garbage collection";
    }

    private static class GarbageCollectorExportsCollector extends Collector {
        private static final GarbageCollectorExports garbageCollectorExports = new GarbageCollectorExports();

        private final String prefix;

        public GarbageCollectorExportsCollector(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public List<MetricFamilySamples> collect() {
            return Util.prefixFromCollector(garbageCollectorExports, prefix);
        }
    }
}