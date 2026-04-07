package com.pred.apitests.util;

import java.util.concurrent.Callable;

/**
 * Exponential-backoff polling for eventual consistency (e.g. order/position visible after Kafka processing).
 * On timeout, fails with a clear message so "Kafka lag suspected" is obvious from the failure.
 */
public final class PollingUtil {

    private PollingUtil() {}

    /**
     * Poll until condition returns true or timeout is reached. Uses exponential backoff: initialDelayMs,
     * then 2x each attempt, capped at maxDelayMs. On timeout, throws AssertionError with timeoutMessage.
     *
     * @param timeoutMs       Total time to wait (e.g. 10_000).
     * @param initialDelayMs  First sleep before first check (e.g. 100). Doubles each attempt.
     * @param maxDelayMs      Cap on sleep between attempts (e.g. 1000).
     * @param timeoutMessage  Message when timeout is reached (e.g. "Kafka lag suspected — order not visible after 10s").
     * @param condition       Returns true when done (e.g. order found in open-orders).
     */
    public static void pollUntil(long timeoutMs, long initialDelayMs, long maxDelayMs,
                                String timeoutMessage, Callable<Boolean> condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long delay = initialDelayMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Boolean.TRUE.equals(condition.call())) return;
            } catch (Exception e) {
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new RuntimeException(e);
            }
            sleep(Math.min(delay, deadline - System.currentTimeMillis()));
            delay = Math.min(delay * 2, maxDelayMs);
        }
        throw new AssertionError(timeoutMessage);
    }

    /**
     * Poll until callable returns non-null or timeout. Same backoff as pollUntil. On timeout, throws AssertionError with timeoutMessage.
     *
     * @return The value returned by condition (non-null).
     */
    public static <T> T pollUntilResult(long timeoutMs, long initialDelayMs, long maxDelayMs,
                                        String timeoutMessage, Callable<T> condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long delay = initialDelayMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                T result = condition.call();
                if (result != null) return result;
            } catch (Exception e) {
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new RuntimeException(e);
            }
            sleep(Math.min(delay, deadline - System.currentTimeMillis()));
            delay = Math.min(delay * 2, maxDelayMs);
        }
        throw new AssertionError(timeoutMessage);
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
