package com.luxpretty.app.mail.app;

/** Permanent send error (invalid email, bad credentials). Do not retry. */
public class HardMailException extends RuntimeException {
    public HardMailException(String message) { super(message); }
    public HardMailException(String message, Throwable cause) { super(message, cause); }
}
