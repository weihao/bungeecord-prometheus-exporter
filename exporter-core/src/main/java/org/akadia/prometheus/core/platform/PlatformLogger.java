package org.akadia.prometheus.core.platform;

/**
 * Minimal logging bridge.
 *
 * <p>BungeeCord hands plugins a {@code java.util.logging.Logger} and Velocity an SLF4J
 * {@code Logger}; neither is on the core classpath, so adapters wrap their own.
 */
public interface PlatformLogger {

    void info(String message);

    void warn(String message);

    void error(String message, Throwable throwable);

    /** Discards everything. Handy in tests and as a null object. */
    static PlatformLogger noop() {
        return new PlatformLogger() {
            @Override
            public void info(String message) {
                // discarded
            }

            @Override
            public void warn(String message) {
                // discarded
            }

            @Override
            public void error(String message, Throwable throwable) {
                // discarded
            }
        };
    }
}
