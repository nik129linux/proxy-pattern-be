package com.proxypattern.error;

/** Maps to HTTP 404. */
public class ResourceNotFoundException extends ServiceException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
