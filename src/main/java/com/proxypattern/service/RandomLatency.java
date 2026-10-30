package com.proxypattern.service;

import java.util.random.RandomGenerator;

/** Simulated network time drawn uniformly from the given range. */
public class RandomLatency implements Latency {

    private final RandomGenerator random;

    public RandomLatency(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public void pause(int minMs, int maxMs) {
        int span = Math.max(0, maxMs - minMs);
        try {
            Thread.sleep(minMs + random.nextInt(span + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
