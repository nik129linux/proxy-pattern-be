package com.proxypattern.error;

public abstract class ServiceException extends RuntimeException {

    private final String code;

    protected ServiceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
