package com.proxypattern.metrics;

import java.time.Instant;

/** Where the logging proxy reports each call. */
public interface CallLog {

    void started(String requestId, String serviceId, String operation, Instant timestamp);

    void finished(LogEntry entry);
}
