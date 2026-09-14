package com.ayush.RateLimiter.slidingWindowLog;

import com.ayush.RateLimiter.RateLimiterStrategy;

import java.util.concurrent.ConcurrentHashMap;

public class SlidingWindowLogRateLimiter implements RateLimiterStrategy {
    private final long maxRequest;
    private final long windowSizeSeconds;
    public SlidingWindowLogRateLimiter(long maxRequest, long windowSizeSeconds){
        this.maxRequest=maxRequest;
        this.windowSizeSeconds=windowSizeSeconds;
    }
    ConcurrentHashMap<String,SlidingWindowLog>slidingWindow=new ConcurrentHashMap<>();
    @Override
    public boolean tryConsume(String clientIp) {
        SlidingWindowLog log=slidingWindow.computeIfAbsent(clientIp,ip-> new SlidingWindowLog(maxRequest,windowSizeSeconds));
       return log.tryConsume();
    }
}
