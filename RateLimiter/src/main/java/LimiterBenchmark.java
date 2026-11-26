//import com.ayush.RateLimiter.RateLimiterStrategy;
//import com.ayush.RateLimiter.fixedwindowcounter.FixedWindowCounterRateLimiter;
//
//public class LimiterBenchmark {
//
//    static final int WARMUP = 20000;
//    static final int RUNS = 10000;
//
//    public static void main(String[] args) {
//        RateLimiterStrategy limiter =new FixedWindowCounterRateLimiter(1000000,60);
//
//        // Warm-up: discard these results
//        for (int i = 0; i < WARMUP; i++) {
//            limiter.tryConsume("bench-user");
//        }
//
//        long[] latencies = new long[RUNS];
//        for (int i = 0; i < RUNS; i++) {
//            long start = System.nanoTime();
//            limiter.tryConsume("bench-user");
//            latencies[i] = System.nanoTime() - start;
//        }
//
//        java.util.Arrays.sort(latencies);
//        long sum = 0;
//        for (long l : latencies) sum += l;
//
//        System.out.println("avg (µs): " + sum / RUNS / 1000.0);
//        System.out.println("p95 (µs): " + latencies[(int) (RUNS * 0.95)] / 1000.0);
//        System.out.println("p99 (µs): " + latencies[(int) (RUNS * 0.99)] / 1000.0);
//    }
//}
import com.ayush.RateLimiter.RateLimiterStrategy;
import com.ayush.RateLimiter.redisBacked.RedisFixedWindowRateLimiter;
import com.ayush.RateLimiter.redisBacked.RedisLueFixedWindowRateLimiter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ayush.RateLimiter.RateLimiterStrategy;
import com.ayush.RateLimiter.redisBacked.RedisFixedWindowRateLimiter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class LimiterBenchmark {

    static final int WARMUP = 20000;
    static final int RUNS = 10000;

    public static void main(String[] args) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet();

        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        RateLimiterStrategy limiter =
                new RedisFixedWindowRateLimiter(1_000_000_000L, 60, template);
        RateLimiterStrategy luaLimiter =
                new RedisLueFixedWindowRateLimiter(1_000_000_000L, 60, template);

        // A: original limiter (INCR, plus EXPIRE on first request)
        template.delete("ratelimit:bench-user");
        report("Limiter tryConsume", measure(() -> limiter.tryConsume("bench-user")));

        // B: raw single INCR
        template.delete("bench-raw");
        report("Raw INCR (1 command)", measure(() -> template.opsForValue().increment("bench-raw")));

        // C: Lua limiter (atomic INCR + EXPIRE in one round trip)
        template.delete("ratelimit:bench-user");
        report("Lua limiter", measure(() -> luaLimiter.tryConsume("bench-user")));

        template.delete("ratelimit:bench-user");
        template.delete("bench-raw");
        factory.destroy();
    }

    static long[] measure(Runnable action) {
        for (int i = 0; i < WARMUP; i++) {
            action.run();
        }
        long[] lat = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            action.run();
            lat[i] = System.nanoTime() - start;
        }
        return lat;
    }

    static void report(String label, long[] lat) {
        java.util.Arrays.sort(lat);
        long sum = 0;
        for (long l : lat) sum += l;
        System.out.println("=== " + label + " ===");
        System.out.println("avg (µs): " + sum / lat.length / 1000.0);
        System.out.println("p95 (µs): " + lat[(int) (lat.length * 0.95)] / 1000.0);
        System.out.println("p99 (µs): " + lat[(int) (lat.length * 0.99)] / 1000.0);
    }
}