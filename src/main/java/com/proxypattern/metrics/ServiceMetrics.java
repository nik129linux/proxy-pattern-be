package com.proxypattern.metrics;

/** Rates are fractions between 0 and 1. */
public record ServiceMetrics(
        String serviceId,
        long totalCalls,
        long successCount,
        long errorCount,
        double successRate,
        double errorRate,
        double averageDurationMs) {
}
