package com.proxypattern.metrics;

import java.util.List;

public record MetricsSummary(
        long totalCalls,
        double errorRate,
        double averageDurationMs,
        List<ServiceMetrics> services) {
}
