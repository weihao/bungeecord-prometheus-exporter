package org.akadia.prometheus.velocity.metrics;

import dev.brighten.antivpn.AntiVPN;
import dev.brighten.antivpn.api.VPNExecutor;
import dev.brighten.antivpn.web.objects.VPNResponse;
import org.akadia.prometheus.interfaces.GauageMetric;
import org.akadia.prometheus.velocity.PrometheusVelocityExporter;

import java.util.Map;

public class OnlinePlayer extends GauageMetric {
    private VPNExecutor executor = AntiVPN.getInstance().getExecutor();

    public OnlinePlayer(PrometheusVelocityExporter plugin) {
        super(plugin);
    }

    @Override
    public void doCollect() {
        this.getGauge().clear();

        ((PrometheusVelocityExporter) getPlugin()).getProxyServer().getAllServers().forEach(registeredServer -> {
            String serverName = registeredServer.getServerInfo().getName();
            this.getGauge().labels(serverName, "", "", "", "", "", "", "", "", "", "").set(0);
            registeredServer.getPlayersConnected().forEach(player -> {
                String ip = player.getRemoteAddress().getAddress().getHostAddress();
                VPNResponse response = executor.checkIp(ip, true);

                this.getGauge().labels(
                        serverName,
                        player.getUsername(),
                        Boolean.toString(player.isOnlineMode()),
                        Boolean.toString(response.isProxy()),
                        response.getMethod(),
                        response.getCountryName(),
                        response.getCountryCode(),
                        response.getIsp(),
                        response.getTimeZone(),
                        Double.toString(response.getLongitude()),
                        Double.toString(response.getLatitude())
                ).set(1);
            });
        });
    }

    @Override
    public String getConfigKey() {
        return "online_player";
    }

    @Override
    public String getHelp() {
        return "the name of the online player in Velocity";
    }

    @Override
    public String[] getLabels() {
        return new String[]{"server", "player", "online_mode", "proxy", "proxy_method", "country_name", "country_code", "isp", "timezone", "longitude", "latitude"};
    }
}
