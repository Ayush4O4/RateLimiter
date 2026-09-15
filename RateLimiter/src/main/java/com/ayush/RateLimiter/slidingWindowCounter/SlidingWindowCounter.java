package com.ayush.RateLimiter.slidingWindowCounter;

import java.util.concurrent.TimeUnit;

public class SlidingWindowCounter {
    private int currentCount;
    private int prevCount;
    private long windowStart;
    private final long maxRequest;
    private final long windowSizeSeconds;

    public SlidingWindowCounter(long maxRequest, long windowSizeSeconds) {
        this.currentCount=0;
        this.prevCount=0;
        this.windowStart=System.nanoTime();
        this.maxRequest = maxRequest;
        this.windowSizeSeconds = windowSizeSeconds;
    }
    public synchronized boolean tryConsume(){
        long now=System.nanoTime();
        long elapsedTime=now- windowStart;
        while(elapsedTime>TimeUnit.SECONDS.toNanos(windowSizeSeconds)){
            prevCount=currentCount;
            currentCount=0;
            windowStart+=TimeUnit.SECONDS.toNanos(windowSizeSeconds);
            elapsedTime=now-windowStart;

        }
        double elapsedInCurrent=now-windowStart;
        double overlapFunction=1.0-(elapsedInCurrent/(double) TimeUnit.SECONDS.toNanos(windowSizeSeconds));
        double estimated=currentCount+prevCount*overlapFunction;

        if(estimated<maxRequest){
            currentCount++;
            return true;
        }
        return false;
    }
}
