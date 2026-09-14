package com.ayush.RateLimiter;

import java.util.concurrent.ConcurrentHashMap;

public class FixedWindowCounterRateLimiter implements RateLimiterStrategy{
    private final long maxRequestsPerWindow;
    private final long windowSizeSeconds;

    public FixedWindowCounterRateLimiter(long maxRequestsPerWindow, long windowSizeSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSizeSeconds = windowSizeSeconds;
    }
    ConcurrentHashMap<String,FixedWindowCounter>windowCounter=new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String clientIp) {
        FixedWindowCounter counter=windowCounter.computeIfAbsent(clientIp,ip-> new FixedWindowCounter(maxRequestsPerWindow,windowSizeSeconds));
        return counter.tryConsume();
    }
}
