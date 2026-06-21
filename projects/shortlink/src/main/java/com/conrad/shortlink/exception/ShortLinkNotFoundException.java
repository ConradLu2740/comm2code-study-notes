package com.conrad.shortlink.exception;

/**
 * 短链接未找到异常
 *
 * 教学点：自定义异常继承 RuntimeException，避免强制 try-catch
 */
public class ShortLinkNotFoundException extends RuntimeException {
    public ShortLinkNotFoundException(String shortCode) {
        super("短链接不存在或已过期: " + shortCode);
    }
}
