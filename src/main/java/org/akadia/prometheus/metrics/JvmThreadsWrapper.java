package org.akadia.prometheus.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.hotspot.ThreadExports;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.MetricWrapper;
import org.akadia.prometheus.utils.Util;

import java.util.List;

public class JvmThreadsWrapper extends MetricWrapper {

    public JvmThreadsWrapper(Plugin plugin) {
        super(plugin, new ThreadExportsCollector());
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

        @Override
        public List<MetricFamilySamples> collect() {
            return Util.prefixFromCollector(threadExports);
        }
    }
}