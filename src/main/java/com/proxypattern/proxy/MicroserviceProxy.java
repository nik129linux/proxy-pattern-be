package com.proxypattern.proxy;

/** Subject of the Proxy pattern: real services and the logging proxy share this contract. */
public interface MicroserviceProxy<T> {

    T execute(String operation, Object... params);
}
