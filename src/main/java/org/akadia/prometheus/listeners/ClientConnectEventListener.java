package org.akadia.prometheus.listeners;

import net.md_5.bungee.api.event.ClientConnectEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.akadia.prometheus.interfaces.CountableMetrics;

public class ClientConnectEventListener extends CountableMetrics {


    public ClientConnectEventListener(Plugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onClientConnectEvent(ClientConnectEvent event) {
        this.getCounter().inc();
    }

    @Override
    public String getConfigKey() {
        return "client_connect_total";
    }

    @Override
    public String getHelp() {
        return "total client connect";
    }


}
