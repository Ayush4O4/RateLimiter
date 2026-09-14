package com.ayush.RateLimiter;

public interface RateLimiterStrategy {
    boolean tryConsume(String ip);
}
