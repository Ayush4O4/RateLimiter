package com.ayush.RateLimiter.redisBacked;

import com.ayush.RateLimiter.RateLimiterStrategy;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisFixedWindowRateLimiter implements RateLimiterStrategy {
    private final long maxRequests;
    private final long windowSizeSeconds;
    private final StringRedisTemplate redisTemplate;

    public RedisFixedWindowRateLimiter(long maxRequests, long windowSizeSeconds, StringRedisTemplate redisTemplate) {
        this.maxRequests = maxRequests;
        this.windowSizeSeconds = windowSizeSeconds;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryConsume(String ip) {
        String key="ratelimit:"+ip;
        Long count=redisTemplate.opsForValue().increment(key);
        if(count!=null && count ==1){
                redisTemplate.expire(key,windowSizeSeconds, TimeUnit.SECONDS);
        }
        return count!=null && count<=maxRequests;
    }
}
