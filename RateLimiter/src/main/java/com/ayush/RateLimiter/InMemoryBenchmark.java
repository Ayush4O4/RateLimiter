package com.ayush.RateLimiter;

import com.ayush.RateLimiter.RateLimiterStrategy;
import com.ayush.RateLimiter.fixedwindowcounter.FixedWindowCounterRateLimiter;

public class InMemoryBenchmark {

    static final int WARMUP = 20000;
    static final int RUNS = 10000;

    public static void main(String[] args) {
        // Adjust constructor args to match yours; very high limit, 60s window
        RateLimiterStrategy limiter = new FixedWindowCounterRateLimiter(1_000_000_000L, 60);

        for (int i = 0; i < WARMUP; i++) limiter.tryConsume("bench-user");

        long[] lat = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            limiter.tryConsume("bench-user");
            lat[i] = System.nanoTime() - start;
        }

        java.util.Arrays.sort(lat);
        long sum = 0;
        for (long l : lat) sum += l;
        System.out.println("avg (µs): " + sum / RUNS / 1000.0);
        System.out.println("p95 (µs): " + lat[(int) (RUNS * 0.95)] / 1000.0);
        System.out.println("p99 (µs): " + lat[(int) (RUNS * 0.99)] / 1000.0);
    }
}
