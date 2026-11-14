package com.sketchtrench.guest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsBurstThenBlocksUntilWindowPasses() {
        RateLimiter r = new RateLimiter(3, 1000);
        assertTrue(r.tryAcquire(0));
        assertTrue(r.tryAcquire(10));
        assertTrue(r.tryAcquire(20));
        assertFalse(r.tryAcquire(30));
        assertFalse(r.tryAcquire(999));
        assertTrue(r.tryAcquire(1000)); // the first event left the window
    }

    @Test
    void warnsOncePerWindow() {
        RateLimiter r = new RateLimiter(1, 1000);
        assertTrue(r.shouldWarn(0));
        assertFalse(r.shouldWarn(500));
        assertTrue(r.shouldWarn(1000));
    }
}
