package com.proxypattern.metrics;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CallLogStore implements CallLog {

    public CallLogStore(@Value("${app.log-capacity:10000}") int capacity) {
    }

    @Override
    public void started(String requestId, String serviceId, String operation, Instant timestamp) {
    }

    @Override
    public void finished(LogEntry entry) {
    }

    public MetricsSummary summary() {
        throw new UnsupportedOperationException();
    }

    public LogPage query(LogQuery query) {
        throw new UnsupportedOperationException();
    }
}
