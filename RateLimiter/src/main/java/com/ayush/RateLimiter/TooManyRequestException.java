package com.ayush.RateLimiter;

public class TooManyRequestException extends RuntimeException{
    private final int statusCode;

    public TooManyRequestException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
