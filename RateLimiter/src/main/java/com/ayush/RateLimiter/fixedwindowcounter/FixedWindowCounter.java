package com.ayush.RateLimiter.fixedwindowcounter;


import java.util.concurrent.TimeUnit;

public class FixedWindowCounter {
    private int count;
    private long windowStart;
    private final long maxRequestPerWindow;
    private final  long  windowSizeSeconds;

    public FixedWindowCounter(long maxRequestPerWindow, long windowSizeSeconds) {
        this.count=0;
        this.windowStart=System.nanoTime();
        this.maxRequestPerWindow = maxRequestPerWindow;
        this.windowSizeSeconds = windowSizeSeconds;
    }
    public synchronized boolean  tryConsume(){
        long now=System.nanoTime();
        if(now-windowStart>= TimeUnit.SECONDS.toNanos(windowSizeSeconds)){
            count=0;
            windowStart=now;
        }
        if(count<maxRequestPerWindow){
            count++;
            return true;
        }
        return false;
    }
}
