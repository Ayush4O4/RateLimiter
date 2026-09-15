package com.ayush.RateLimiter.slidingWindowCounter;

import com.ayush.RateLimiter.RateLimiterStrategy;

import java.util.concurrent.ConcurrentHashMap;

public class SlidingWindowCounterRateLimiter implements RateLimiterStrategy {
    private final long maxRequests;
    private final long windowSizeSeconds;

    public SlidingWindowCounterRateLimiter(long maxRequests, long windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeSeconds = windowSizeSeconds;
    }
    ConcurrentHashMap<String,SlidingWindowCounter>windowCounter=new ConcurrentHashMap<>();
    @Override
    public boolean tryConsume(String clientIp) {
        SlidingWindowCounter counter=windowCounter.computeIfAbsent(clientIp,ip-> new SlidingWindowCounter(maxRequests,windowSizeSeconds));
        return counter.tryConsume();
    }
}
