package com.dylan.store.blob;

import com.dylan.store.StoreException;

public class BlobFormatException extends StoreException {

    private static final long serialVersionUID = 1L;

    public BlobFormatException(String message) {
        super(message);
    }

    public BlobFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
