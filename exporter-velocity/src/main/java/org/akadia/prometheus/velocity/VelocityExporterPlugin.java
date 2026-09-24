package org.akadia.prometheus.velocity;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.akadia.prometheus.core.ProxyExporter;
import org.akadia.prometheus.core.config.ConfigLoader;
import org.akadia.prometheus.core.config.ConfigurationException;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.platform.PlatformLogger;
import org.akadia.prometheus.core.platform.RedisBungeeNetworkStats;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Velocity plugin entry point.
 *
 * <p>The plugin descriptor lives in {@code velocity-plugin.json} in the distribution
 * module rather than in a {@code @Plugin} annotation, so the version is filtered straight
 * from the POM instead of being a string that drifts out of date.
 */
public final class VelocityExporterPlugin {

    /** bStats plugin id, shared with the BungeeCord build so the stats are one plugin. */
    private static final int BSTATS_PLUGIN_ID = 11269;

    private final ProxyServer proxy;
    private final PlatformLogger logger;
    private final Path dataDirectory;
    private final Injector injector;

    private ProxyExporter exporter;

    /**
     * @param injector used to resolve bStats lazily. Injecting {@code Metrics.Factory}
     *                 directly would make plugin construction fail outright if anything
     *                 about bStats could not be satisfied, taking the metrics endpoint
     *                 down with it. Anonymous usage statistics are not worth that.
     */
    @Inject
    public VelocityExporterPlugin(ProxyServer proxy,
                                  Logger logger,
                                  @DataDirectory Path dataDirectory,
                                  Injector injector) {
        this.proxy = proxy;
        this.logger = new Slf4jPlatformLogger(logger);
        this.dataDirectory = dataDirectory;
        this.injector = injector;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        ExporterConfig config;
        try {
            config = ConfigLoader.load(dataDirectory, logger::warn);
        } catch (ConfigurationException e) {
            logger.error("Invalid configuration; the exporter will not start: " + e.getMessage(), e);
            return;
        }

        VelocityPlatform platform = new VelocityPlatform(proxy, logger);

        try {
            ProxyExporter.Builder builder = ProxyExporter.builder(config, platform);
            if (config.isEnabled(MetricId.REDISBUNGEE)) {
                // RedisBungee ships a Velocity build with the same API class, so the same
                // reflective binding works here.
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
            proxy.getEventManager().register(this, new VelocityEventListener(exporter.events()));
        }

        if (config.bstats()) {
            startBStats();
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        proxy.getEventManager().unregisterListeners(this);
        if (exporter != null) {
            exporter.close();
            exporter = null;
        }
    }

    private void startBStats() {
        try {
            injector.getInstance(Metrics.Factory.class).make(this, BSTATS_PLUGIN_ID);
        } catch (RuntimeException | LinkageError e) {
            logger.warn("bStats could not start: " + e.getMessage());
        }
    }

    /** Bridges SLF4J, which is what Velocity injects into plugins. */
    private record Slf4jPlatformLogger(Logger delegate) implements PlatformLogger {

        @Override
        public void info(String message) {
            delegate.info(message);
        }

        @Override
        public void warn(String message) {
            delegate.warn(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            delegate.error(message, throwable);
        }
    }
}
