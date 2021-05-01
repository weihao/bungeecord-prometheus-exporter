package org.akadia.prometheus;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.akadia.prometheus.listeners.LoginEventListener;
import org.akadia.prometheus.listeners.PlayerDisconnectEventListener;
import org.akadia.prometheus.listeners.PostLoginEventListener;
import org.akadia.prometheus.listeners.PreLoginEventListener;
import org.akadia.prometheus.listeners.ProxyPingEventListener;
import org.akadia.prometheus.listeners.ClientConnectEventListener;
import org.akadia.prometheus.metrics.Metric;
import org.akadia.prometheus.metrics.PlayersOnlineTotal;
import org.akadia.prometheus.metrics.ServersOnlineTotal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

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

        Metric m;

        m = new PlayersOnlineTotal(this);
        m.enable();
        MetricRegistry.getInstance().register(m);

        m = new ServersOnlineTotal(this);
        m.enable();
        MetricRegistry.getInstance().register(m);



        getProxy().getPluginManager().registerListener(this, new ClientConnectEventListener());
        getProxy().getPluginManager().registerListener(this, new LoginEventListener());
        getProxy().getPluginManager().registerListener(this, new PlayerDisconnectEventListener());
        getProxy().getPluginManager().registerListener(this, new PostLoginEventListener());
        getProxy().getPluginManager().registerListener(this, new PreLoginEventListener());
        getProxy().getPluginManager().registerListener(this, new ProxyPingEventListener());

        try {
            server.start();
            getLogger().info("Started Prometheus metrics endpoint at: " + host + ":" + port);
        } catch (Exception e) {
            getLogger().severe("Could not start embedded Jetty server");
        }

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
