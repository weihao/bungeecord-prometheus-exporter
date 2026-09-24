package org.akadia.prometheus.core.support;

import org.akadia.prometheus.core.ProxyExporter;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.platform.NetworkStatsProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.function.UnaryOperator;

/**
 * Starts a real {@link ProxyExporter} on an ephemeral port and talks to it over real HTTP.
 *
 * <p>Port 0 rather than a fixed port so the suite can run in parallel and in CI without
 * two tests racing for 9985.
 */
public final class ExporterHarness implements AutoCloseable {

    private final FakeProxyPlatform platform;
    private final ProxyExporter exporter;
    private final HttpClient client;

    private ExporterHarness(FakeProxyPlatform platform, ProxyExporter exporter) {
        this.platform = platform;
        this.exporter = exporter;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** Starts an exporter with default config against {@code platform}. */
    public static ExporterHarness start(FakeProxyPlatform platform) {
        return start(platform, UnaryOperator.identity());
    }

    /** Starts an exporter, letting the caller adjust the config first. */
    public static ExporterHarness start(FakeProxyPlatform platform,
                                        UnaryOperator<ExporterConfig.Builder> customiser) {
        return start(platform, customiser, null, Clock.systemUTC());
    }

    public static ExporterHarness start(FakeProxyPlatform platform,
                                        UnaryOperator<ExporterConfig.Builder> customiser,
                                        NetworkStatsProvider networkStats,
                                        Clock clock) {
        ExporterConfig config = customiser
                .apply(ExporterConfig.builder()
                        .host("127.0.0.1")
                        // Port 0 so the suite can run in parallel and in CI without two
                        // tests fighting over 9985.
                        .port(0)
                        // Samplers are registered but not scheduled, so tests drive them
                        // through runSamplersNow() and see exact, repeatable counts rather
                        // than racing a background thread. Tests that care about the
                        // scheduling itself opt back in.
                        .backendPingInterval(Duration.ZERO)
                        .playerPingSampleInterval(Duration.ZERO))
                .build();
        try {
            ProxyExporter exporter = ProxyExporter.builder(config, platform)
                    .networkStats(networkStats)
                    .clock(clock)
                    .start();
            return new ExporterHarness(platform, exporter);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the exporter under test", e);
        }
    }

    public ProxyExporter exporter() {
        return exporter;
    }

    public FakeProxyPlatform platform() {
        return platform;
    }

    public int port() {
        return exporter.port();
    }

    /** Scrapes the registry directly, bypassing HTTP. */
    public ScrapedMetrics scrape() {
        return ScrapedMetrics.from(exporter.registry());
    }

    public ScrapedMetrics scrapeOpenMetrics() {
        return ScrapedMetrics.openMetricsFrom(exporter.registry());
    }

    /** Fetches {@code path} over HTTP with no credentials. */
    public HttpResponse<String> get(String path) {
        return get(path, null, null, null);
    }

    /** Fetches {@code path} over HTTP, optionally with basic auth and an Accept header. */
    public HttpResponse<String> get(String path, String accept, String username, String password) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port() + path))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (accept != null) {
            request.header("Accept", accept);
        }
        if (username != null) {
            String token = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + token);
        }
        try {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("HTTP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    /** Fetches the configured metrics path over HTTP and parses the body. */
    public ScrapedMetrics fetchMetrics() {
        HttpResponse<String> response = get(exporter.config().path());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("metrics endpoint returned " + response.statusCode());
        }
        return ScrapedMetrics.parseText(response.body());
    }

    @Override
    public void close() {
        exporter.close();
    }
}
