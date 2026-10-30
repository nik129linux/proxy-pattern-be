package com.proxypattern.error;

/** Maps to HTTP 400. */
public class InvalidParamsException extends ServiceException {

    public InvalidParamsException(String message) {
        super("INVALID_PARAMS", message);
    }
}
