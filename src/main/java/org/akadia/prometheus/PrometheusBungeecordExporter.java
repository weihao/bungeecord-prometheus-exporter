package org.akadia.prometheus;

import io.prometheus.client.CollectorRegistry;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.akadia.prometheus.interfaces.Configurable;
import org.akadia.prometheus.interfaces.CountableMetrics;
import org.akadia.prometheus.interfaces.Metric;
import org.akadia.prometheus.interfaces.MetricWrapper;
import org.akadia.prometheus.listeners.ClientConnectEventListener;
import org.akadia.prometheus.listeners.LoginEventListener;
import org.akadia.prometheus.listeners.PlayerDisconnectEventListener;
import org.akadia.prometheus.listeners.PostLoginEventListener;
import org.akadia.prometheus.listeners.PreLoginEventListener;
import org.akadia.prometheus.listeners.ProxyPingEventListener;
import org.akadia.prometheus.listeners.RedisPlayerJoinedNetworkEventListener;
import org.akadia.prometheus.listeners.RedisPlayerLeftNetworkEventListener;
import org.akadia.prometheus.metrics.JvmGarbageCollectorWrapper;
import org.akadia.prometheus.metrics.JvmMemory;
import org.akadia.prometheus.metrics.JvmThreadsWrapper;
import org.akadia.prometheus.metrics.PlayersOnlineTotal;
import org.akadia.prometheus.metrics.RedisBungeePlayersOnlineTotal;
import org.akadia.prometheus.metrics.RedisBungeeProxyOnlineTotal;
import org.akadia.prometheus.metrics.ServersOnlineTotal;
import org.bstats.bungeecord.Metrics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PrometheusBungeecordExporter extends Plugin {
    final String CONFIG_FILENAME = "config.yml";
    Configuration config;


    @Override
    public void onEnable() {
        initializeConfig();
        startMetricsServer();
    }

    private void startMetricsServer() {
        String host = config.getString("host", "127.0.0.1");
        Integer port = config.getInt("port", 9225);

        MetricsServer server = new MetricsServer(host, port, this);

        List<Configurable> configurables = new ArrayList<>();
        configurables.add(new ClientConnectEventListener(this));
        configurables.add(new LoginEventListener(this));
        configurables.add(new PlayerDisconnectEventListener(this));
        configurables.add(new PostLoginEventListener(this));
        configurables.add(new PreLoginEventListener(this));
        configurables.add(new ProxyPingEventListener(this));
        configurables.add(new JvmGarbageCollectorWrapper(this));
        configurables.add(new JvmMemory(this));
        configurables.add(new JvmThreadsWrapper(this));
        configurables.add(new PlayersOnlineTotal(this));
        configurables.add(new ServersOnlineTotal(this));

        configurables.add(new RedisPlayerJoinedNetworkEventListener(this));
        configurables.add(new RedisPlayerLeftNetworkEventListener(this));
        configurables.add(new RedisBungeePlayersOnlineTotal(this));
        configurables.add(new RedisBungeeProxyOnlineTotal(this));

        for (Configurable configurable : configurables) {
            String configKey = "enable_metrics." + configurable.getConfigKey();
            if (!config.getBoolean(configKey)) {
                getLogger().info(configKey + " is disabled in the config");
                continue;
            }
            getLogger().info(configKey + " is enabled in the config");
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
            getLogger().info("Started Prometheus metrics endpoint at: " + host + ":" + port);
        } catch (Exception e) {
            getLogger().severe("Could not start embedded Jetty server");
        }
        try {
            Metrics metrics = new Metrics(this, 11269);
        } catch (IllegalStateException ex) {
            getLogger().info("Metrics failed to start");
        }
        getLogger().info("Initialized completed");
    }

    public void initializeConfig() {
        createFile(CONFIG_FILENAME, true);

        config = load(CONFIG_FILENAME);


    }

    public Configuration load(String filename) {
        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public File createFile(String filename, boolean copyFromResource) {
        File saveTo = null;
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdir();
            }

            saveTo = new File(dataFolder, filename);
            if (!saveTo.exists()) {
                if (copyFromResource) {
                    InputStream in = getResourceAsStream(filename);
                    Files.copy(in, saveTo.toPath());
                } else {
                    saveTo.createNewFile();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return saveTo;
    }
}
