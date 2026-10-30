package com.proxypattern.proxy;

import com.proxypattern.metrics.CallLog;
import com.proxypattern.metrics.CallStatus;
import com.proxypattern.metrics.LogEntry;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Proxy that records every call and then delegates. */
public class LoggingProxy<T> implements MicroserviceProxy<T> {

    private static final int STACK_FRAMES = 5;

    private final String serviceId;
    private final MicroserviceProxy<T> delegate;
    private final CallLog callLog;
    private final Clock clock;

    public LoggingProxy(String serviceId, MicroserviceProxy<T> delegate, CallLog callLog, Clock clock) {
        this.serviceId = serviceId;
        this.delegate = delegate;
        this.callLog = callLog;
        this.clock = clock;
    }

    @Override
    public T execute(String operation, Object... params) {
        String requestId = UUID.randomUUID().toString();
        Instant timestamp = clock.instant();
        long start = System.nanoTime();
        callLog.started(requestId, serviceId, operation, timestamp);
        try {
            T response = delegate.execute(operation, params);
            callLog.finished(entry(requestId, operation, start, timestamp, params,
                    CallStatus.SUCCESS, response, null));
            return response;
        } catch (RuntimeException e) {
            callLog.finished(entry(requestId, operation, start, timestamp, params,
                    CallStatus.ERROR, null, e));
            throw e;
        }
    }

    private LogEntry entry(String requestId, String operation, long start, Instant timestamp,
            Object[] params, CallStatus status, Object response, RuntimeException error) {
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return new LogEntry(requestId, serviceId, operation, durationMs, status, timestamp,
                Arrays.asList(params), response,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                error == null ? null : stack(error));
    }

    private static List<String> stack(RuntimeException error) {
        return Arrays.stream(error.getStackTrace())
                .limit(STACK_FRAMES)
                .map(StackTraceElement::toString)
                .toList();
    }
}
