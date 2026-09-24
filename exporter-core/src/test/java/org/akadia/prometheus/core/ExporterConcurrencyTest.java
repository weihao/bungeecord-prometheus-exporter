package org.akadia.prometheus.core;

import org.akadia.prometheus.core.config.MetricId;
import org.akadia.prometheus.core.metrics.EventMetrics;
import org.akadia.prometheus.core.support.ExporterHarness;
import org.akadia.prometheus.core.support.FakeBackendServer;
import org.akadia.prometheus.core.support.FakeProxyPlatform;
import org.akadia.prometheus.core.support.ScrapedMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scrapes, events and world churn all happen at once on a real proxy.
 *
 * <p>The HTTP server answers on its own thread pool, the samplers run on another thread,
 * and proxy events arrive on Netty threads - all while players join and leave and servers
 * are registered. This test runs all of that simultaneously and asserts the two properties
 * that must hold: nothing throws, and counters never go backwards.
 */
class ExporterConcurrencyTest {

    private static final String NS = "minecraft_proxy_";

    @Test
    @Timeout(120)
    @DisplayName("concurrent scrapes, events and world churn produce no errors and no lost counts")
    void concurrentLoad() throws Exception {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addServer(FakeBackendServer.named("survival").failing())
                .addPlugin("exporter", "4.0.0");

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST)
                .enable(MetricId.PLAYER_PING_PER_PLAYER)
                .enable(MetricId.COMMAND_LABEL)
                .maxSeriesPerMetric(500))) {

            EventMetrics events = harness.exporter().events();
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicLong expectedChats = new AtomicLong();
            AtomicLong lastChatSeen = new AtomicLong();

            int eventThreads = 4;
            int scrapeThreads = 3;
            ExecutorService pool = Executors.newFixedThreadPool(eventThreads + scrapeThreads + 2);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(eventThreads + scrapeThreads + 2);

            // Event producers.
            for (int t = 0; t < eventThreads; t++) {
                int id = t;
                pool.submit(guarded(failures, done, start, () -> {
                    for (int i = 0; i < 4000; i++) {
                        events.recordConnection();
                        events.recordLogin(i % 7 != 0);
                        events.recordChat();
                        expectedChats.incrementAndGet();
                        events.recordCommand("/cmd" + (i % 25));
                        events.recordKick("server-" + (i % 5));
                        events.recordServerSwitch("lobby", "server-" + (i % 5));
                        events.recordDisconnect();
                        events.recordListPing();
                        if (i % 500 == 0) {
                            Thread.yield();
                        }
                    }
                    // Touch the id so the lambda is not accidentally identical per thread.
                    events.recordCommand("/thread" + id);
                }));
            }

            // Scrapers, each asserting that the counter never moves backwards.
            //
            // Monotonicity is per observer, not global: two threads scraping at once can
            // finish out of order, so a thread that started earlier legitimately returns a
            // smaller value than one that started later. Prometheus sees a single ordered
            // sequence per target, which is what each thread models here.
            for (int t = 0; t < scrapeThreads; t++) {
                pool.submit(guarded(failures, done, start, () -> {
                    double previous = 0;
                    while (running.get()) {
                        ScrapedMetrics scrape = harness.scrape();
                        double chats = scrape.value(NS + "chat_messages_total").orElse(0.0d);
                        if (chats < previous) {
                            throw new AssertionError(
                                    "counter went backwards: " + chats + " < " + previous);
                        }
                        previous = chats;
                        lastChatSeen.accumulateAndGet((long) chats, Math::max);
                    }
                }));
            }

            // World churn: players joining and leaving under the collectors' feet.
            pool.submit(guarded(failures, done, start, () -> {
                while (running.get()) {
                    platform.addPlayers(40, "lobby", 30);
                    platform.removeAllPlayers();
                }
            }));

            // Server registration churn, plus sampler runs.
            pool.submit(guarded(failures, done, start, () -> {
                int generation = 0;
                while (running.get()) {
                    platform.addServer(FakeBackendServer.named("transient-" + generation));
                    harness.exporter().runSamplersNow();
                    platform.removeServer("transient-" + generation);
                    generation++;
                }
            }));

            start.countDown();
            Thread.sleep(3_000);
            running.set(false);
            assertThat(done.await(90, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            assertThat(failures).isEmpty();

            ScrapedMetrics finalScrape = harness.scrape();
            assertThat(finalScrape.value(NS + "chat_messages_total"))
                    .as("no increment may be lost under contention")
                    .contains((double) expectedChats.get());
            assertThat(finalScrape.value(NS + "exporter_scrape_errors_total",
                    "collector", "player_online"))
                    .as("no collector may fail under concurrent world churn")
                    .isEmpty();
            assertThat(harness.get("/metrics").statusCode()).isEqualTo(200);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("concurrent HTTP scrapes all succeed")
    void concurrentHttpScrapes() throws Exception {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addPlayers(200, "lobby", 40);

        try (ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .enable(MetricId.PLAYER_LIST)
                .maxSeriesPerMetric(1000))) {
            // Four simultaneous scrapers is already well beyond a normal deployment, where
            // Prometheus scrapes with a single connection. The Prometheus client library
            // hardcodes the TCP accept backlog to 3, so a far larger burst of *simultaneous
            // connection attempts* can be refused by the OS before the server accepts them;
            // that is a property of the library, not of this exporter, and it is documented
            // on MetricsHttpServer.
            int clients = 4;
            ExecutorService pool = Executors.newFixedThreadPool(clients);
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            CountDownLatch done = new CountDownLatch(clients);
            CountDownLatch start = new CountDownLatch(1);

            for (int i = 0; i < clients; i++) {
                pool.submit(guarded(failures, done, start, () -> {
                    for (int request = 0; request < 15; request++) {
                        int status = harness.get("/metrics").statusCode();
                        if (status != 200) {
                            throw new AssertionError("scrape returned " + status);
                        }
                    }
                }));
            }

            start.countDown();
            assertThat(done.await(45, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            assertThat(failures).isEmpty();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("closing while scrapes are in flight does not deadlock")
    void closeUnderLoad() throws Exception {
        FakeProxyPlatform platform = new FakeProxyPlatform()
                .addServer(FakeBackendServer.named("lobby"))
                .addPlayers(50, "lobby", 25);

        ExporterHarness harness = ExporterHarness.start(platform, builder -> builder
                .backendPingInterval(Duration.ofMillis(100)));
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                while (running.get()) {
                    try {
                        harness.get("/metrics");
                    } catch (RuntimeException e) {
                        // Expected once the server stops accepting.
                        return;
                    }
                }
            });
        }

        Thread.sleep(500);
        harness.close();
        running.set(false);
        pool.shutdownNow();

        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS))
                .as("close must not leave scrape threads wedged")
                .isTrue();
    }

    private static Runnable guarded(List<Throwable> failures,
                                    CountDownLatch done,
                                    CountDownLatch start,
                                    ThrowingRunnable body) {
        return () -> {
            try {
                start.await();
                body.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                done.countDown();
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
