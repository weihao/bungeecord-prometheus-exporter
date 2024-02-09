package org.akadia.prometheus.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.prometheus.client.CollectorRegistry;
import org.akadia.prometheus.MetricRegistry;
import org.akadia.prometheus.MetricsServer;
import org.akadia.prometheus.PrometheusExporter;
import org.akadia.prometheus.bungeecord.listeners.PlayerCommandEventListener;
import org.akadia.prometheus.config.ConfigManager;
import org.akadia.prometheus.interfaces.Configurable;
import org.akadia.prometheus.interfaces.CountableMetrics;
import org.akadia.prometheus.interfaces.Metric;
import org.akadia.prometheus.interfaces.MetricWrapper;
import org.akadia.prometheus.metrics.JvmGarbageCollectorWrapper;
import org.akadia.prometheus.metrics.JvmMemory;
import org.akadia.prometheus.metrics.JvmThreadsWrapper;
import org.akadia.prometheus.velocity.listeners.LoginEventListener;
import org.akadia.prometheus.velocity.listeners.PlayerChatEventListener;
import org.akadia.prometheus.velocity.listeners.PlayerDisconnectEventListener;
import org.akadia.prometheus.velocity.listeners.ProxyPingEventListener;
import org.akadia.prometheus.velocity.metrics.InstalledNetworkPlugins;
import org.akadia.prometheus.velocity.metrics.ManagedServers;
import org.akadia.prometheus.velocity.metrics.OnlinePlayer;
import org.akadia.prometheus.velocity.metrics.OnlinePlayersLatency;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Plugin(id = "velocity-prometheus-exporter", name = "Velocity Prometheus Exporter", version = "1.0.0", authors = "akadia")
public class PrometheusVelocityExporter implements PrometheusExporter {
    @Inject
    @DataDirectory
    public Path configDir;
    @Inject
    private Metrics.Factory metricsFactory;
    @Inject
    private ProxyServer proxyServer;

    @Inject
    private Logger logger;
    private String prefix;

    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            ConfigManager configManager = new ConfigManager(configDir.toFile());
            startMetricsServer(configManager);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startMetricsServer(ConfigManager configManager) {
        try {
            Metrics metrics = metricsFactory.make(this, 11269);
        } catch (IllegalStateException ex) {
            this.info("bStats Metrics failed to start");
        }

        this.prefix = configManager.getConfig().getOrDefault("prefix", "bungeecord_");

        String host = configManager.getConfig().getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(configManager.getConfig().getOrDefault("port", "9985"));

        MetricsServer server = new MetricsServer(host, port, this);

        List<Configurable> configurables = new ArrayList<>();
        configurables.add(new LoginEventListener(this));
        configurables.add(new PlayerDisconnectEventListener(this));
        configurables.add(new PlayerChatEventListener(this));
        configurables.add(new PlayerCommandEventListener(this));
        configurables.add(new ProxyPingEventListener(this));
        configurables.add(new JvmGarbageCollectorWrapper(this));
        configurables.add(new JvmMemory(this));
        configurables.add(new JvmThreadsWrapper(this));
        configurables.add(new OnlinePlayer(this));
        configurables.add(new OnlinePlayersLatency(this));
        configurables.add(new ManagedServers(this));
        configurables.add(new InstalledNetworkPlugins(this));

        for (Configurable configurable : configurables) {
            if (configManager.getConfig().getOrDefault(configurable.getConfigKey(), "true").equals("false")) {
                logger.info(configurable.getConfigKey() + " is disabled in the config");
                continue;
            }
            this.info(configurable.getConfigKey() + " is enabled in the config");
            if (configurable instanceof CountableMetrics) {
                proxyServer.getEventManager().register(this, configurable);
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
        this.logger.info(info);
    }

    @Override
    public void warn(String warning) {
        this.logger.warn(warning);
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }
}
