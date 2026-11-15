package com.sketchtrench.guest;

import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Readers snapshot the player map while writers add/remove — must never throw. */
class RoomConcurrencyTest {

    @RepeatedTest(5)
    void snapshotsSurviveConcurrentJoinsAndLeaves() throws Exception {
        GameRoom room = new GameRoom("r", "R", false, 1000, 3, 60, false);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            boolean writer = t % 2 == 0;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2_000; i++) {
                        if (writer) {
                            GuestPlayer p = new GuestPlayer("p", "#fff", "happy", false, "none", "en");
                            room.players.put(p.playerId, p);
                            room.players.remove(p.playerId);
                        } else {
                            room.orderedPlayers();
                            room.humanPlayers();
                        }
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get();
        pool.shutdown();
        assertEquals(0, errors.get());
        assertTrue(room.players.isEmpty());
    }
}
