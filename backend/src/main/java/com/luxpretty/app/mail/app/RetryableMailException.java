package com.luxpretty.app.mail.app;

/** Transient send error (5xx, timeout, IO). Caller should retry with backoff. */
public class RetryableMailException extends RuntimeException {
    public RetryableMailException(String message) { super(message); }
    public RetryableMailException(String message, Throwable cause) { super(message, cause); }
}
