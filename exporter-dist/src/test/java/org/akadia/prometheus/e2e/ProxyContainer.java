package org.akadia.prometheus.e2e;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * A real BungeeCord or Velocity proxy in Docker, running the shaded plugin jar.
 *
 * <p>Everything below this point is verified against fakes and mocks, which cannot catch
 * the things that actually break a Minecraft plugin: a plugin descriptor the proxy will
 * not parse, a relocated dependency that vanishes at runtime, an {@code @Inject}
 * constructor the proxy's injector cannot satisfy, bytecode the runtime JVM refuses. Those
 * only show up when a real proxy loads the real jar, which is what this does.
 */
final class ProxyContainer extends GenericContainer<ProxyContainer> {

    /** itzg's image supports both proxies through the TYPE variable. */
    private static final DockerImageName IMAGE = DockerImageName.parse("itzg/mc-proxy:latest");

    static final int METRICS_PORT = 9985;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ProxyContainer(String type, Path pluginJar) {
        super(IMAGE);
        withEnv("TYPE", type);
        withEnv("MEMORY", "512m");
        // The proxy has no backend servers here, which is deliberate: the exporter must
        // come up and serve metrics on an otherwise empty proxy.
        withExposedPorts(METRICS_PORT);
        withCopyFileToContainer(
                MountableFile.forHostPath(pluginJar),
                "/server/plugins/proxy-prometheus-exporter.jar");
        // Bind to all interfaces so the port mapping can reach it from outside the container.
        withEnv("MC_PROXY_EXPORTER_HOST", "0.0.0.0");
        withEnv("MC_PROXY_EXPORTER_PORT", String.valueOf(METRICS_PORT));
        // Anonymous statistics from a throwaway CI container would be noise in bStats.
        withEnv("MC_PROXY_EXPORTER_BSTATS", "false");
        withLogConsumer(new Slf4jLogConsumer(
                org.slf4j.LoggerFactory.getLogger("proxy-" + type.toLowerCase())));
        waitingFor(Wait.forListeningPorts(METRICS_PORT)
                .withStartupTimeout(Duration.ofMinutes(5)));
    }

    static ProxyContainer velocity(Path pluginJar) {
        return new ProxyContainer("VELOCITY", pluginJar);
    }

    static ProxyContainer bungeecord(Path pluginJar) {
        return new ProxyContainer("BUNGEECORD", pluginJar);
    }

    static ProxyContainer waterfall(Path pluginJar) {
        return new ProxyContainer("WATERFALL", pluginJar);
    }

    /** Fetches the exposition body from the running proxy. */
    String fetchMetrics() {
        return get("/metrics").body();
    }

    HttpResponse<String> get(String path) {
        String url = "http://" + getHost() + ":" + getMappedPort(METRICS_PORT) + path;
        try {
            return client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("could not reach " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    /** The shaded plugin jar under test, passed in by failsafe. */
    static Path pluginJar() {
        String path = System.getProperty("exporter.jar");
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "system property 'exporter.jar' is not set; run through maven-failsafe");
        }
        Path jar = Path.of(path);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("plugin jar not found at " + jar
                    + "; run 'mvn package' first");
        }
        return jar;
    }
}
