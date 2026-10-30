package com.proxypattern.metrics;

import java.time.Instant;
import java.util.List;

public record LogEntry(
        String requestId,
        String serviceId,
        String operation,
        long durationMs,
        CallStatus status,
        Instant timestamp,
        List<Object> params,
        Object response,
        String errorType,
        String errorMessage,
        List<String> stackSummary) {
}
