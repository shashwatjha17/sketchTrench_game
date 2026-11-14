package com.sketchtrench.guest;

import java.util.ArrayDeque;
import java.util.Deque;

/** Sliding-window limiter: at most {@code max} events per {@code windowMillis}. One per player. */
public class RateLimiter {

    private final int max;
    private final long windowMillis;
    private final Deque<Long> events = new ArrayDeque<>();
    private long lastWarnedAt = Long.MIN_VALUE / 2;

    public RateLimiter(int max, long windowMillis) {
        this.max = max;
        this.windowMillis = windowMillis;
    }

    /** Records the event and returns true if it's within the limit; false means drop it. */
    public synchronized boolean tryAcquire(long nowMillis) {
        while (!events.isEmpty() && nowMillis - events.peekFirst() >= windowMillis) {
            events.pollFirst();
        }
        if (events.size() >= max) return false;
        events.addLast(nowMillis);
        return true;
    }

    /** True at most once per window, so a spammer gets one "slow down" notice, not one per message. */
    public synchronized boolean shouldWarn(long nowMillis) {
        if (nowMillis - lastWarnedAt < windowMillis) return false;
        lastWarnedAt = nowMillis;
        return true;
    }
}
