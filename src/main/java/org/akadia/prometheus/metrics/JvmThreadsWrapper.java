package org.akadia.prometheus.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.hotspot.ThreadExports;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.interfaces.MetricWrapper;
import org.akadia.prometheus.utils.Util;

import java.util.List;

public class JvmThreadsWrapper extends MetricWrapper {

    public JvmThreadsWrapper(PrometheusExporter plugin) {
        super(plugin, new ThreadExportsCollector(plugin.getPrefix()));
    }

    @Override
    public void doCollect() {
    }


    @Override
    public String getConfigKey() {
        return "jvm_threads";
    }

    @Override
    public String getHelp() {
        return "JVM threads usage";
    }

    private static class ThreadExportsCollector extends Collector {
        private static final ThreadExports threadExports = new ThreadExports();
        private final String prefix;

        public ThreadExportsCollector(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public List<MetricFamilySamples> collect() {
            return Util.prefixFromCollector(threadExports, prefix);
        }
    }
}