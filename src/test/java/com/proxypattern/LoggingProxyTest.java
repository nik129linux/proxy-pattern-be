package com.proxypattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.proxypattern.metrics.CallLog;
import com.proxypattern.metrics.CallStatus;
import com.proxypattern.metrics.LogEntry;
import com.proxypattern.proxy.LoggingProxy;
import com.proxypattern.proxy.MicroserviceProxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoggingProxyTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private final RecordingLog log = new RecordingLog();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsDelegateResultAndLogsEveryRequiredField() {
        MicroserviceProxy<String> proxy = new LoggingProxy<>("inventory", (op, params) -> op + "-done", log, clock);
        Map<String, Object> body = Map.of("sku", "SKU-1001");

        String result = proxy.execute("check", body);

        assertThat(result).isEqualTo("check-done");
        assertThat(log.entries).hasSize(1);
        LogEntry entry = log.entries.get(0);
        assertThat(entry.serviceId()).isEqualTo("inventory");
        assertThat(entry.operation()).isEqualTo("check");
        assertThat(entry.status()).isEqualTo(CallStatus.SUCCESS);
        assertThat(entry.timestamp()).isEqualTo(NOW);
        assertThat(UUID.fromString(entry.requestId()).toString()).isEqualTo(entry.requestId());
        assertThat(entry.params()).containsExactly(body);
        assertThat(entry.response()).isEqualTo("check-done");
        assertThat(entry.errorType()).isNull();
        assertThat(entry.errorMessage()).isNull();
        assertThat(entry.stackSummary()).isNullOrEmpty();
    }

    @Test
    void recordsTheStartBeforeTheDelegateRuns() {
        List<Integer> startsSeenByDelegate = new ArrayList<>();
        MicroserviceProxy<String> proxy = new LoggingProxy<>("orders", (op, params) -> {
            startsSeenByDelegate.add(log.starts.size());
            return "ok";
        }, log, clock);

        proxy.execute("list");

        assertThat(startsSeenByDelegate).containsExactly(1);
        assertThat(log.starts).containsExactly(log.entries.get(0).requestId());
    }

    @Test
    void measuresTheDurationOfTheDelegate() {
        MicroserviceProxy<String> proxy = new LoggingProxy<>("orders", (op, params) -> {
            sleep(30);
            return "ok";
        }, log, clock);

        proxy.execute("list");

        assertThat(log.entries.get(0).durationMs()).isBetween(30L, 2000L);
    }

    @Test
    void logsTheFailureAndRethrowsTheSameException() {
        IllegalStateException failure = new IllegalStateException("gateway down");
        MicroserviceProxy<String> proxy = new LoggingProxy<>("payments", (op, params) -> {
            throw failure;
        }, log, clock);

        assertThatThrownBy(() -> proxy.execute("charge")).isSameAs(failure);

        LogEntry entry = log.entries.get(0);
        assertThat(entry.status()).isEqualTo(CallStatus.ERROR);
        assertThat(entry.errorType()).isEqualTo("IllegalStateException");
        assertThat(entry.errorMessage()).isEqualTo("gateway down");
        assertThat(entry.response()).isNull();
        assertThat(entry.stackSummary()).hasSizeBetween(1, 5);
        assertThat(entry.stackSummary().get(0)).contains("LoggingProxyTest");
    }

    @Test
    void wrapsAnyServiceType() {
        MicroserviceProxy<Integer> proxy = new LoggingProxy<>("numbers", (op, params) -> params.length, log, clock);

        assertThat(proxy.execute("count", "a", "b")).isEqualTo(2);
        assertThat(log.entries.get(0).params()).containsExactly("a", "b");
    }

    @Test
    void givesEveryCallItsOwnRequestId() {
        MicroserviceProxy<String> proxy = new LoggingProxy<>("orders", (op, params) -> "ok", log, clock);

        for (int i = 0; i < 100; i++) {
            proxy.execute("list");
        }

        Set<String> ids = new HashSet<>();
        log.entries.forEach(entry -> ids.add(entry.requestId()));
        assertThat(ids).hasSize(100);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class RecordingLog implements CallLog {
        final List<String> starts = new ArrayList<>();
        final List<LogEntry> entries = new ArrayList<>();

        @Override
        public void started(String requestId, String serviceId, String operation, Instant timestamp) {
            starts.add(requestId);
        }

        @Override
        public void finished(LogEntry entry) {
            entries.add(entry);
        }
    }
}
