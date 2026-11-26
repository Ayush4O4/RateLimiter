package com.ayush.RateLimiter.redisBacked;

import com.ayush.RateLimiter.RateLimiterStrategy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

public class RedisLueFixedWindowRateLimiter implements RateLimiterStrategy {

    private final long maxRequests;
    private final long windowSizeSeconds;
    private final StringRedisTemplate redisTemplate;


    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) " +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                    "return c",
            Long.class);

    public RedisLueFixedWindowRateLimiter(long maxRequests, long windowSizeSeconds, StringRedisTemplate redisTemplate) {
        this.maxRequests = maxRequests;
        this.windowSizeSeconds = windowSizeSeconds;
        this.redisTemplate = redisTemplate;
    }

    public boolean tryConsume(String ip) {
        String key = "ratelimit:" + ip;
        Long count = redisTemplate.execute(SCRIPT, List.of(key), String.valueOf(windowSizeSeconds));
        return count != null && count <= maxRequests;
    }
}
