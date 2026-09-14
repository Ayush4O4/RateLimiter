package com.ayush.RateLimiter.slidingWindowLog;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public class SlidingWindowLog {

    private final long maxRequest;
    private final long windowSizeSeconds;
    Deque<Long>timeStamps;

    public SlidingWindowLog(long maxRequest, long windowSizeSeconds) {
        this.timeStamps=new ConcurrentLinkedDeque<>();
        this.maxRequest = maxRequest;
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public synchronized boolean tryConsume(){
        long now=System.nanoTime();
        long staleTime=now- TimeUnit.SECONDS.toNanos(windowSizeSeconds);
        while(!timeStamps.isEmpty() && timeStamps.peekFirst()<staleTime){
            timeStamps.removeFirst();
        }
        if(timeStamps.size()<maxRequest){
            timeStamps.addLast(now);
            return true;
        }
        return false;
    }
}
