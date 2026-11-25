package com.dylan.pvf;

public class PvfFormatException extends RuntimeException {

    public PvfFormatException(String message) {
        super(message);
    }

    public PvfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
