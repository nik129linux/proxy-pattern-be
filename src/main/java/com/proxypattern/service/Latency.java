package com.proxypattern.service;

/** Simulated network time of a real service call. */
public interface Latency {

    void pause(int minMs, int maxMs);

    static Latency none() {
        return (minMs, maxMs) -> { };
    }
}
