package com.dylan.protocol.io;

public class PacketFormatException extends RuntimeException {

    public PacketFormatException(String message) {
        super(message);
    }

    public PacketFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
