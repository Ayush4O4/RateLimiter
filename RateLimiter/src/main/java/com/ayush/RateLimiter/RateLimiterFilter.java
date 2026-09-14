package com.ayush.RateLimiter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimiterFilter implements Filter {

    private final ConcurrentHashMap<String,TockenBucket> buckets=new ConcurrentHashMap<>();
    public TockenBucket getBucketForClient(String clientIp){
        return buckets.computeIfAbsent(clientIp,ip->new TockenBucket(10,1.0));
    }
    @Override
    public void doFilter(ServletRequest request, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        if(((HttpServletRequest) request).getRequestURI().equals("/limited")){
            String ip=request.getRemoteAddr();
            TockenBucket bucket=getBucketForClient(ip);
            if(bucket.tryConsume()){
                filterChain.doFilter(request,servletResponse);
            }
            else{
                HttpServletResponse httpResponse=(HttpServletResponse)servletResponse;
                httpResponse.setStatus(429);
                httpResponse.getWriter().write("Too many Requests, wait some time");
            }
        }
        else{
            filterChain.doFilter(request,servletResponse);
        }

    }
}
