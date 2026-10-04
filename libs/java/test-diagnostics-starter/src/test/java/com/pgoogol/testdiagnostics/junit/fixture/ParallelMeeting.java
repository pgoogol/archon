package com.pgoogol.testdiagnostics.junit.fixture;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * Spotkanie testów z dwóch klas: każdy czeka, aż drugi też wystartuje. Udaje się tylko
 * wtedy, gdy obie klasy trwają naraz, więc udany przebieg dowodzi równoległego wykonania.
 */
public final class ParallelMeeting {

    private static volatile CyclicBarrier barrier = new CyclicBarrier(2);

    private ParallelMeeting() {
    }

    public static void reset() {

        barrier = new CyclicBarrier(2);
    }

    static void await() throws Exception {

        barrier.await(10, TimeUnit.SECONDS);
    }
}
