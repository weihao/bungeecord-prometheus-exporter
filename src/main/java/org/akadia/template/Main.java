package org.akadia.template;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public class Main extends Plugin implements Listener {
    private static Configuration configuration;

    @Override
    public void onEnable() {
        getLogger().info("Yay! It loads!");
        PluginManager pm = getProxy().getPluginManager();
        pm.registerListener(this, this);
        pm.registerCommand(this, new Lobby());

        TaskScheduler scheduler = getProxy().getScheduler();
        scheduler.runAsync(this, () -> getLogger().info("Hello from Runnable"));
        scheduler.schedule(this, () -> {
            for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
                player.sendMessage(
                        new ComponentBuilder("Currently online players count: ")
                                .color(ChatColor.GRAY)
                                .append(String.valueOf(getProxy().getOnlineCount()))
                                .color(ChatColor.AQUA)
                                .create());
            }
        }, 0, 10, TimeUnit.SECONDS);


        createFile("config.yml", true);

        try {
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        getLogger().info(String.valueOf(configuration.getInt("version")));
        configuration.set("write", "fromPlugin");
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(configuration, new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        logToFile("Server logging");
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
            player.sendMessage(new TextComponent(event.getPlayer().getName() + " has joined the network."));
        }
    }

    public void logToFile(String message) {
        try {
            File saveTo = createFile("log.txt");
            FileWriter fw = new FileWriter(saveTo, true);
            PrintWriter pw = new PrintWriter(fw);
            pw.println(message);
            pw.flush();
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File createFile(String filename) {
        return createFile(filename, false);
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