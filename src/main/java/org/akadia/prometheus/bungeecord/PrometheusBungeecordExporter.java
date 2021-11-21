package org.akadia.prometheus.bungeecord;

import io.prometheus.client.CollectorRegistry;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.MetricRegistry;
import org.akadia.prometheus.MetricsServer;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.bungeecord.listeners.LoginEventListener;
import org.akadia.prometheus.bungeecord.listeners.PlayerDisconnectEventListener;
import org.akadia.prometheus.bungeecord.listeners.PlayerJoinedNetworkEventListener;
import org.akadia.prometheus.bungeecord.listeners.PlayerLeftNetworkEventListener;
import org.akadia.prometheus.bungeecord.listeners.ProxyPingEventListener;
import org.akadia.prometheus.bungeecord.metrics.ManagedServers;
import org.akadia.prometheus.bungeecord.metrics.OnlinePlayers;
import org.akadia.prometheus.bungeecord.metrics.RedisBungeeOnlinePlayers;
import org.akadia.prometheus.bungeecord.metrics.RedisBungeeOnlineProxies;
import org.akadia.prometheus.config.ConfigManager;
import org.akadia.prometheus.interfaces.Configurable;
import org.akadia.prometheus.interfaces.CountableMetrics;
import org.akadia.prometheus.interfaces.Metric;
import org.akadia.prometheus.interfaces.MetricWrapper;
import org.akadia.prometheus.metrics.JvmGarbageCollectorWrapper;
import org.akadia.prometheus.metrics.JvmMemory;
import org.akadia.prometheus.metrics.JvmThreadsWrapper;
import org.bstats.bungeecord.Metrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrometheusBungeecordExporter extends Plugin implements PrometheusExporter {
    String prefix;

    @Override
    public void onEnable() {
        try {
            ConfigManager configManager = new ConfigManager(getDataFolder());
            startMetricsServer(configManager);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startMetricsServer(ConfigManager configManager) {
        try {
            Metrics metrics = new Metrics(this, 11269);
        } catch (IllegalStateException ex) {
            getLogger().info("bStats Metrics failed to start");
        }

        this.prefix = configManager.getConfig().getOrDefault("prefix", "bungeecord_");

        String host = configManager.getConfig().getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(configManager.getConfig().getOrDefault("port", "9225"));

        MetricsServer server = new MetricsServer(host, port, this);

        List<Configurable> configurables = new ArrayList<>();
        configurables.add(new LoginEventListener(this));
        configurables.add(new PlayerDisconnectEventListener(this));
        configurables.add(new ProxyPingEventListener(this));
        configurables.add(new JvmGarbageCollectorWrapper(this));
        configurables.add(new JvmMemory(this));
        configurables.add(new JvmThreadsWrapper(this));
        configurables.add(new OnlinePlayers(this));
        configurables.add(new ManagedServers(this));

        configurables.add(new PlayerJoinedNetworkEventListener(this));
        configurables.add(new PlayerLeftNetworkEventListener(this));
        configurables.add(new RedisBungeeOnlinePlayers(this));
        configurables.add(new RedisBungeeOnlineProxies(this));

        for (Configurable configurable : configurables) {
            if (configManager.getConfig().getOrDefault(configurable.getConfigKey(), "true").equals("false")) {
                this.info(configurable.getConfigKey() + " is disabled in the config");
                continue;
            }
            this.info(configurable.getConfigKey() + " is enabled in the config");
            if (configurable instanceof CountableMetrics) {
                this.getProxy().getPluginManager().registerListener(this, (Listener) configurable);
            } else if (configurable instanceof MetricWrapper) {
                CollectorRegistry.defaultRegistry.register(((MetricWrapper) configurable).getCollector());
            } else {
                MetricRegistry.getInstance().register((Metric) configurable);
            }
        }

        try {
            server.start();
            this.info("Started Prometheus metrics endpoint at: " + host + ":" + port);
        } catch (Exception e) {
            this.warn("Could not start embedded Jetty server");
        }

        this.info("Initialized completed");
    }

    @Override
    public void info(String info) {
        this.getLogger().info(info);
    }

    @Override
    public void warn(String warning) {
        this.getLogger().warning(warning);
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }
}
