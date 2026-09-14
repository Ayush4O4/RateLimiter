package com.ayush.RateLimiter;

import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketRateLimiter implements RateLimiterStrategy {
    private final int capacity;
    private final double refillRatePerSecond;
    private final ConcurrentHashMap<String,TockenBucket> buckets=new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }


    @Override
    public boolean tryConsume(String clientIp) {
      TockenBucket bucket=buckets.computeIfAbsent(clientIp,ip->new TockenBucket(capacity,refillRatePerSecond));
      return bucket.tryConsume();
    }
}
