package org.akadia.prometheus.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.hotspot.ThreadExports;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.interfaces.Configurable;
import org.akadia.prometheus.interfaces.Metric;
import org.akadia.prometheus.utils.Util;

import java.util.List;

public class JvmThreadsWrapper extends Metric implements Configurable {

    public JvmThreadsWrapper(Plugin plugin) {
        super(plugin);
    }

    @Override
    protected void doCollect() {
    }

    @Override
    public String getConfigKey() {
        return null;
    }

    @Override
    public String getHelp() {
        return null;
    }

    private static class ThreadExportsCollector extends Collector {
        private static final ThreadExports threadExports = new ThreadExports();

        @Override
        public List<MetricFamilySamples> collect() {
            return Util.prefixFromCollector(threadExports);
        }
    }
}