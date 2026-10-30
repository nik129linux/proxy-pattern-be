package com.proxypattern.error;

/** Maps to HTTP 409. */
public class BusinessRuleException extends ServiceException {

    public BusinessRuleException(String message) {
        super("BUSINESS_RULE", message);
    }
}
