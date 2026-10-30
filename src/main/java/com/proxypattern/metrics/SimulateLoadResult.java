package com.proxypattern.metrics;

public record SimulateLoadResult(int generated, int success, int error, long durationMs) {
}
