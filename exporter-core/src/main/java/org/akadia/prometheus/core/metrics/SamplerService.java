package org.akadia.prometheus.core.metrics;

import org.akadia.prometheus.core.platform.PlatformLogger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the background samplers.
 *
 * <p>Anything that needs network I/O or a full iteration of the player list happens here
 * rather than inside a scrape callback. A Prometheus scrape has a deadline; if the
 * exporter blocks on a dead backend the whole target times out and every metric is lost.
 * Sampling on an interval and serving the cached result keeps {@code /metrics} fast and
 * predictable no matter what the network is doing.
 *
 * <p>Threads are daemons so a failure to shut down cleanly cannot keep the JVM alive.
 */
public final class SamplerService implements AutoCloseable {

    private final ScheduledExecutorService executor;
    private final ExporterSelfMetrics selfMetrics;
    private final PlatformLogger logger;
    // Written while the exporter is being constructed and read from whichever thread
     // calls runAllNow(), so it needs safe publication rather than a plain HashMap.
    private final Map<String, Runnable> tasks = new ConcurrentHashMap<>();

    public SamplerService(ExporterSelfMetrics selfMetrics, PlatformLogger logger) {
        this(Executors.newScheduledThreadPool(1, daemonThreadFactory()), selfMetrics, logger);
    }

    SamplerService(ScheduledExecutorService executor,
                   ExporterSelfMetrics selfMetrics,
                   PlatformLogger logger) {
        this.executor = executor;
        this.selfMetrics = selfMetrics;
        this.logger = logger;
    }

    /**
     * Schedules {@code task} to run every {@code period}, starting immediately.
     *
     * <p>The first run has no delay so that cached metrics such as
     * {@code <namespace>_server_up} exist from the first scrape onwards. Waiting a full
     * interval would leave those series absent right after a restart, and an alert on
     * {@code server_up == 0} cannot fire on a series that does not exist yet.
     *
     * <p>A zero or negative period disables the sampler, which is how operators turn off
     * backend pings entirely.
     */
    public void schedule(String name, Duration period, Runnable task) {
        // Registered even when the interval disables it, so that runAllNow() can still
        // drive the sampler on demand. That is what lets a test set the interval to zero
        // and get exact, repeatable sample counts instead of racing the scheduler.
        Runnable instrumented = instrument(name, task);
        tasks.put(name, instrumented);

        if (period == null || period.isZero() || period.isNegative()) {
            logger.info("Sampler '" + name + "' is disabled (interval is zero)");
            return;
        }
        long millis = Math.max(period.toMillis(), 100L);
        executor.scheduleWithFixedDelay(instrumented, 0, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs every scheduled sampler once on the calling thread.
     *
     * <p>Lets tests assert on sampled metrics without sleeping for an interval, which is
     * the difference between a deterministic suite and a flaky one.
     */
    public void runAllNow() {
        tasks.values().forEach(Runnable::run);
    }

    /**
     * Wraps a sampler body with timing and error containment.
     *
     * <p>{@code scheduleWithFixedDelay} silently cancels a task that throws, which would
     * leave the exporter serving stale data forever with no signal. Swallowing here keeps
     * the schedule alive and turns the failure into a counter instead.
     */
    Runnable instrument(String name, Runnable task) {
        return () -> {
            long startNanos = System.nanoTime();
            boolean success = true;
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                success = false;
                logger.error("Sampler '" + name + "' failed; it will run again next interval", e);
            } finally {
                double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0d;
                selfMetrics.recordSamplerRun(name, success, seconds);
            }
        };
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Sampler threads did not stop within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "prometheus-exporter-sampler-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
