package org.akadia.prometheus.core.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardinalityGuardTest {

    @Test
    @DisplayName("combinations are admitted up to the budget and refused after it")
    void admitsUpToTheBudget() {
        CardinalityGuard guard = new CardinalityGuard(3, CardinalityGuard.DropListener.NOOP);

        assertThat(guard.admit("m", "a")).isTrue();
        assertThat(guard.admit("m", "b")).isTrue();
        assertThat(guard.admit("m", "c")).isTrue();
        assertThat(guard.admit("m", "d")).isFalse();
        assertThat(guard.admittedCount("m")).isEqualTo(3);
    }

    @Test
    @DisplayName("an already-admitted combination keeps reporting after the budget is hit")
    void admittedSeriesStayContinuous() {
        // A series that vanishes mid-graph looks like an outage; once admitted it must keep
        // being emitted even though the metric is now at its limit.
        CardinalityGuard guard = new CardinalityGuard(2, CardinalityGuard.DropListener.NOOP);
        guard.admit("m", "lobby");
        guard.admit("m", "survival");
        guard.admit("m", "creative");

        assertThat(guard.admit("m", "lobby")).isTrue();
        assertThat(guard.admit("m", "survival")).isTrue();
        assertThat(guard.admit("m", "creative")).isFalse();
    }

    @Test
    @DisplayName("label tuples that share a concatenation are still distinct")
    void labelTuplesDoNotCollide() {
        // Server names come from the proxy's own config and may contain any character,
        // including whatever separator a naive key encoding would join on. {"a b", "c"}
        // and {"a", "b c"} must stay two series, not silently become one.
        CardinalityGuard guard = new CardinalityGuard(10, CardinalityGuard.DropListener.NOOP);

        guard.admit("m", "a b", "c");
        guard.admit("m", "a", "b c");
        guard.admit("m", "a", "bc");
        guard.admit("m", "ab", "c");

        assertThat(guard.admittedCount("m")).isEqualTo(4);
    }

    @Test
    @DisplayName("an empty label value is distinct from an absent one")
    void emptyLabelValuesAreDistinct() {
        CardinalityGuard guard = new CardinalityGuard(10, CardinalityGuard.DropListener.NOOP);

        guard.admit("m", "", "x");
        guard.admit("m", "x", "");
        guard.admit("m", "x");

        assertThat(guard.admittedCount("m")).isEqualTo(3);
    }

    @Test
    @DisplayName("a null label value is tolerated and treated as empty")
    void nullLabelValueIsTolerated() {
        CardinalityGuard guard = new CardinalityGuard(10, CardinalityGuard.DropListener.NOOP);

        assertThat(guard.admit("m", (String) null)).isTrue();
        assertThat(guard.admit("m", "")).isTrue();
        assertThat(guard.admittedCount("m")).isEqualTo(1);
    }

    @Test
    @DisplayName("the budget is per metric, not global")
    void budgetIsPerMetric() {
        CardinalityGuard guard = new CardinalityGuard(1, CardinalityGuard.DropListener.NOOP);

        assertThat(guard.admit("first", "x")).isTrue();
        assertThat(guard.admit("second", "x")).isTrue();
        assertThat(guard.admit("first", "y")).isFalse();
    }

    @Test
    @DisplayName("label values are matched as a tuple, not concatenated")
    void multipleLabelsFormOneKey() {
        CardinalityGuard guard = new CardinalityGuard(10, CardinalityGuard.DropListener.NOOP);

        guard.admit("m", "a", "b");
        guard.admit("m", "a", "c");

        assertThat(guard.admittedCount("m")).isEqualTo(2);
    }

    @Test
    @DisplayName("every drop is counted and only the first one is flagged for logging")
    void dropsAreCountedOnceAndLoggedOnce() {
        AtomicLong dropped = new AtomicLong();
        AtomicInteger firstTimeFlags = new AtomicInteger();
        CardinalityGuard guard = new CardinalityGuard(1, (metric, count, firstTime) -> {
            dropped.addAndGet(count);
            if (firstTime) {
                firstTimeFlags.incrementAndGet();
            }
        });

        guard.admit("m", "kept");
        guard.admit("m", "dropped-1");
        guard.admit("m", "dropped-2");
        guard.admit("m", "dropped-3");

        assertThat(dropped).hasValue(3);
        assertThat(firstTimeFlags)
                .as("a log line per dropped series would be a log flood")
                .hasValue(1);
    }

    @Test
    @DisplayName("the first-time flag is tracked per metric")
    void firstTimeFlagIsPerMetric() {
        List<String> flagged = new CopyOnWriteArrayList<>();
        CardinalityGuard guard = new CardinalityGuard(1, (metric, count, firstTime) -> {
            if (firstTime) {
                flagged.add(metric);
            }
        });

        guard.admit("a", "1");
        guard.admit("a", "2");
        guard.admit("b", "1");
        guard.admit("b", "2");

        assertThat(flagged).containsExactly("a", "b");
    }

    @Test
    @DisplayName("reset clears both the admitted set and the warning state")
    void resetClearsEverything() {
        AtomicInteger firstTimeFlags = new AtomicInteger();
        CardinalityGuard guard = new CardinalityGuard(1, (metric, count, firstTime) -> {
            if (firstTime) {
                firstTimeFlags.incrementAndGet();
            }
        });
        guard.admit("m", "a");
        guard.admit("m", "b");

        guard.reset();

        assertThat(guard.admittedCount("m")).isZero();
        assertThat(guard.admit("m", "b")).isTrue();
        guard.admit("m", "c");
        assertThat(firstTimeFlags).hasValue(2);
    }

    @Test
    @DisplayName("a budget below one is rejected")
    void budgetMustBePositive() {
        assertThatThrownBy(() -> new CardinalityGuard(0, CardinalityGuard.DropListener.NOOP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("concurrent admits stay within a bounded overshoot of the budget")
    void concurrentAdmitsStayBounded() throws InterruptedException {
        int budget = 50;
        int threads = 8;
        int perThread = 500;
        CardinalityGuard guard = new CardinalityGuard(budget, CardinalityGuard.DropListener.NOOP);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        guard.admit("m", "t" + threadId + "-" + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // The check-then-add is deliberately unsynchronised to keep the scrape path cheap,
        // so racing threads can overshoot by at most one series each. What must never
        // happen is unbounded growth.
        assertThat(guard.admittedCount("m"))
                .isGreaterThanOrEqualTo(budget)
                .isLessThanOrEqualTo(budget + threads);
    }
}
