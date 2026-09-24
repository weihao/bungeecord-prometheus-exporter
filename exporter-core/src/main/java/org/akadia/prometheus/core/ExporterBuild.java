package org.akadia.prometheus.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build metadata baked into the jar by Maven resource filtering. */
public final class ExporterBuild {

    private static final String RESOURCE = "exporter-build.properties";
    private static final String UNKNOWN = "unknown";

    private static final Properties PROPERTIES = load();

    private ExporterBuild() {
    }

    /** Exporter version, e.g. {@code "4.0.0"}. */
    public static String version() {
        return PROPERTIES.getProperty("version", UNKNOWN);
    }

    /** Java {@code --release} the jar was compiled for. */
    public static String javaTarget() {
        return PROPERTIES.getProperty("javaTarget", UNKNOWN);
    }

    /** Velocity API revision compiled against. */
    public static String velocityApi() {
        return PROPERTIES.getProperty("velocityApi", UNKNOWN);
    }

    /** BungeeCord API revision compiled against. */
    public static String bungeecordApi() {
        return PROPERTIES.getProperty("bungeecordApi", UNKNOWN);
    }

    /** Prometheus client library version bundled in the jar. */
    public static String prometheusClient() {
        return PROPERTIES.getProperty("prometheusClient", UNKNOWN);
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = ExporterBuild.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Build metadata is cosmetic; a missing file must never stop the exporter.
        }
        return properties;
    }
}
