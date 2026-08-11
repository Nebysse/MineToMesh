package com.nebysse.minetomesh.job;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CaptureBudgetTest {
    @Test
    void expiresAtSixMillisecondsUsingAMonotonicClock() {
        AtomicLong now = new AtomicLong(1_000_000L);
        CaptureBudget budget = CaptureBudget.start(Duration.ofMillis(6), now::get);

        assertTrue(budget.hasTime());
        now.addAndGet(5_999_999L);
        assertTrue(budget.hasTime());
        now.incrementAndGet();
        assertFalse(budget.hasTime());
    }

    @Test
    void cancellationKeepsTheFirstReason() {
        CancellationToken token = new CancellationToken();

        assertTrue(token.cancel("disconnect"));
        assertFalse(token.cancel("reload"));
        assertTrue(token.isCancelled());
        assertTrue(token.reason().orElseThrow().equals("disconnect"));
    }
}
