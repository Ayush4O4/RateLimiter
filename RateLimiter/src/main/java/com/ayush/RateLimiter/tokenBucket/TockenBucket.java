package com.ayush.RateLimiter.tokenBucket;


public class TockenBucket {
    private int capacity;
    private double tokens;
    private double refillRatePerSecond;
    private long lastRefillTimeStamp;



    public TockenBucket(int capacity, double refillRatePerSecond) {
        this.capacity=capacity;
        this.refillRatePerSecond=refillRatePerSecond;
        this.tokens = capacity;
        this.lastRefillTimeStamp = System.nanoTime();
    }

    public void refill(){  //lazy refill only check how many tokens should exist when a request arrive
        long now=System.nanoTime();
        long elapsedNanos=now-lastRefillTimeStamp;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double tokensToAdd=elapsedSeconds*refillRatePerSecond;
        tokens=Math.min(capacity,tokens+tokensToAdd);
        lastRefillTimeStamp=now;
    }

    public synchronized boolean tryConsume(){
        refill();
        if(tokens>=1){
            tokens--;
            return true;
        }
        return false;
    }



}
