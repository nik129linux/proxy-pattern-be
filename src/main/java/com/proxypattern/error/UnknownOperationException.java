package com.proxypattern.error;

/** Maps to HTTP 404. */
public class UnknownOperationException extends ServiceException {

    public UnknownOperationException(String message) {
        super("UNKNOWN_OPERATION", message);
    }
}
