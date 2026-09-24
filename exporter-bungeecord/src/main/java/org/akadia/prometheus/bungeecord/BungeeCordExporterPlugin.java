package org.akadia.prometheus.bungeecord;

import net.md_5.bungee.api.plugin.Plugin;
import org.akadia.prometheus.core.ProxyExporter;
import org.akadia.prometheus.core.config.ConfigLoader;
import org.akadia.prometheus.core.config.ConfigurationException;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.PlatformLogger;
import org.akadia.prometheus.core.platform.RedisBungeeNetworkStats;
import org.bstats.bungeecord.Metrics;

import java.io.IOException;
import java.util.logging.Level;

/** BungeeCord / Waterfall plugin entry point. */
public final class BungeeCordExporterPlugin extends Plugin {

    /** bStats plugin id, shared with the Velocity build so the stats are one plugin. */
    private static final int BSTATS_PLUGIN_ID = 11269;

    private ProxyExporter exporter;

    @Override
    public void onEnable() {
        PlatformLogger logger = new JulPlatformLogger(getLogger());

        ExporterConfig config;
        try {
            config = ConfigLoader.load(getDataFolder().toPath(), logger::warn);
        } catch (ConfigurationException e) {
            // A bad config is an operator error, not a crash: say exactly what is wrong and
            // leave the proxy running without metrics.
            logger.error("Invalid configuration; the exporter will not start: " + e.getMessage(), e);
            return;
        }

        BungeeCordPlatform platform = new BungeeCordPlatform(getProxy(), logger);

        try {
            ProxyExporter.Builder builder = ProxyExporter.builder(config, platform);
            if (config.isEnabled(MetricId.REDISBUNGEE)) {
                builder.networkStats(
                        RedisBungeeNetworkStats.lazyBinding(getClass().getClassLoader()));
            }
            exporter = builder.start();
        } catch (IOException e) {
            logger.error("Could not bind the metrics endpoint to " + config.host() + ":"
                    + config.port() + "; the exporter will not start", e);
            return;
        }

        if (exporter.events().isEnabled()) {
            getProxy().getPluginManager()
                    .registerListener(this, new BungeeCordEventListener(exporter.events()));
        }

        if (config.bstats()) {
            startBStats(logger);
        }
    }

    @Override
    public void onDisable() {
        // Unregistering the listeners first stops events arriving at metrics that are about
        // to be torn down; closing the exporter releases the port so a reload can rebind it.
        getProxy().getPluginManager().unregisterListeners(this);
        if (exporter != null) {
            exporter.close();
            exporter = null;
        }
    }

    private void startBStats(PlatformLogger logger) {
        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
        } catch (RuntimeException | LinkageError e) {
            logger.warn("bStats could not start: " + e.getMessage());
        }
    }

    /** Bridges {@code java.util.logging}, which is what BungeeCord hands plugins. */
    private record JulPlatformLogger(java.util.logging.Logger delegate) implements PlatformLogger {

        @Override
        public void info(String message) {
            delegate.info(message);
        }

        @Override
        public void warn(String message) {
            delegate.warning(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            delegate.log(Level.SEVERE, message, throwable);
        }
    }
}
