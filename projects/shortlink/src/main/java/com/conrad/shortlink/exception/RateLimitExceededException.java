package com.conrad.shortlink.exception;

/**
 * 限流异常
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("请求过于频繁，请稍后再试");
    }
}
