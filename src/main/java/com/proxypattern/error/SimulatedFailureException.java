package com.proxypattern.error;

/** Maps to HTTP 503. */
public class SimulatedFailureException extends ServiceException {

    public SimulatedFailureException(String message) {
        super("SIMULATED_FAILURE", message);
    }
}
