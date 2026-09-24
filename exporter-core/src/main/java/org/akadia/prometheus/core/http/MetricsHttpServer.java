package org.akadia.prometheus.core.http;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.platform.PlatformLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code /metrics} endpoint.
 *
 * <p>Built on the Prometheus client's own {@link HTTPServer}, which runs on the JDK's
 * {@code com.sun.net.httpserver}. The previous exporter embedded Jetty 11 for this, which
 * meant shading a servlet container and {@code jakarta.servlet} into a plugin jar that
 * gets loaded next to arbitrary other plugins - a reliable source of
 * {@code NoClassDefFoundError} reports. The JDK server has no such footprint and brings
 * content negotiation, gzip and OpenMetrics support with it.
 */
public final class MetricsHttpServer implements AutoCloseable {

    private final ExporterConfig config;
    private final PrometheusRegistry registry;
    private final PlatformLogger logger;
    private HTTPServer server;

    public MetricsHttpServer(ExporterConfig config, PrometheusRegistry registry, PlatformLogger logger) {
        this.config = config;
        this.registry = registry;
        this.logger = logger;
    }

    /**
     * Binds and starts serving.
     *
     * @throws IOException if the port is taken or the host cannot be resolved
     */
    public void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("server is already running");
        }

        HTTPServer.Builder builder = HTTPServer.builder()
                .port(config.port())
                .registry(registry)
                .metricsHandlerPath(config.path())
                // /-/healthy answers without touching the collectors, so an orchestrator
                // can probe liveness without paying for a full scrape.
                .registerHealthHandler(true)
                // Without this the library serves metrics from "/" as well, so every URL
                // on the port returns a full scrape. That makes the configured path
                // cosmetic and turns any stray request - a health check, a port scanner,
                // a misconfigured probe - into a full collection pass.
                .defaultHandler(MetricsHttpServer::notFound)
                .executorService(scrapeExecutor());

        try {
            builder.inetAddress(InetAddress.getByName(config.host()));
        } catch (UnknownHostException e) {
            throw new IOException("cannot resolve host '" + config.host() + "'", e);
        }

        if (config.authEnabled()) {
            builder.authenticator(basicAuthenticator());
        }

        this.server = builder.buildAndStart();

        logger.info("Prometheus metrics available at http://" + config.host() + ":" + port()
                + config.path() + (config.authEnabled() ? " (basic auth required)" : ""));
    }

    /**
     * The bound port.
     *
     * <p>Differs from {@code config.port()} when 0 was configured, which is how tests get
     * an ephemeral port without racing each other.
     */
    public int port() {
        return server == null ? -1 : server.getPort();
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
            server = null;
            logger.info("Prometheus metrics endpoint stopped");
        }
    }

    /**
     * Thread pool for serving scrapes.
     *
     * <p>The library's default is ten threads that are created on demand and then live
     * forever. A Minecraft proxy is thread-sensitive and this endpoint is typically hit
     * once every fifteen seconds by a single client, so ten permanent threads is a real
     * cost for no benefit. Four threads that retire after a minute of idleness handle
     * concurrent scrapes just as well and leave nothing behind between them.
     *
     * <p>Naming them matters too: {@code prometheus-exporter-http-1} in a proxy's thread
     * dump tells an operator immediately which plugin owns it.
     *
     * <p>Note that the library hardcodes the TCP accept backlog to 3, which is not
     * configurable through its builder. That is ample for Prometheus, which scrapes with
     * one connection, but a burst of simultaneous connection attempts can be refused by
     * the OS before any of these threads sees them.
     */
    private static ExecutorService scrapeExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4, 4, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "prometheus-exporter-http-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /** Answers anything that is not the metrics path or the health path. */
    private static void notFound(HttpExchange exchange) throws IOException {
        byte[] body = "404 Not Found\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(404, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private Authenticator basicAuthenticator() {
        return new ConstantTimeBasicAuthenticator(
                "metrics", config.authUsername(), config.authPassword());
    }
}
