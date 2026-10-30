package com.proxypattern;

import static org.assertj.core.api.Assertions.assertThat;

import com.proxypattern.metrics.CallLogStore;
import com.proxypattern.metrics.CallStatus;
import com.proxypattern.metrics.LogEntry;
import com.proxypattern.metrics.LogPage;
import com.proxypattern.metrics.LogQuery;
import com.proxypattern.metrics.MetricsSummary;
import com.proxypattern.metrics.ServiceMetrics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CallLogStoreTest {

    private static final Instant T0 = Instant.parse("2026-10-30T12:00:00Z");

    private final CallLogStore store = new CallLogStore(100);

    private static LogEntry entry(String service, CallStatus status, long ms, Instant at) {
        return new LogEntry(UUID.randomUUID().toString(), service, "op", ms, status, at, List.of(), null, null, null, null);
    }

    private LogPage all() {
        return store.query(new LogQuery(null, null, null, null, 0, 100));
    }

    private static ServiceMetrics metrics(MetricsSummary summary, String service) {
        return summary.services().stream().filter(s -> s.serviceId().equals(service)).findFirst().orElseThrow();
    }

    @Test
    void emptySummaryStillListsTheThreeServicesInOrder() {
        MetricsSummary summary = store.summary();

        assertThat(summary.totalCalls()).isZero();
        assertThat(summary.errorRate()).isZero();
        assertThat(summary.averageDurationMs()).isZero();
        assertThat(summary.services()).extracting(ServiceMetrics::serviceId)
                .containsExactly("inventory", "orders", "payments");
        assertThat(summary.services().get(0).successRate()).isZero();
    }

    @Test
    void summaryComputesRatesAndAveragesPerService() {
        store.finished(entry("payments", CallStatus.SUCCESS, 100, T0));
        store.finished(entry("payments", CallStatus.SUCCESS, 200, T0));
        store.finished(entry("payments", CallStatus.SUCCESS, 300, T0));
        store.finished(entry("payments", CallStatus.ERROR, 400, T0));
        store.finished(entry("orders", CallStatus.SUCCESS, 50, T0));

        MetricsSummary summary = store.summary();
        ServiceMetrics payments = metrics(summary, "payments");

        assertThat(payments.totalCalls()).isEqualTo(4);
        assertThat(payments.successCount()).isEqualTo(3);
        assertThat(payments.errorCount()).isEqualTo(1);
        assertThat(payments.errorRate()).isEqualTo(0.25);
        assertThat(payments.successRate()).isEqualTo(0.75);
        assertThat(payments.averageDurationMs()).isEqualTo(250.0);
        assertThat(summary.totalCalls()).isEqualTo(5);
        assertThat(summary.errorRate()).isEqualTo(0.2);
        assertThat(summary.averageDurationMs()).isEqualTo(210.0);
    }

    @Test
    void capacityEvictsOldEntriesButKeepsCounting() {
        CallLogStore small = new CallLogStore(3);
        for (int i = 0; i < 5; i++) {
            small.finished(entry("orders", CallStatus.SUCCESS, 10, T0.plusSeconds(i)));
        }

        LogPage page = small.query(new LogQuery(null, null, null, null, 0, 100));

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(0).timestamp()).isEqualTo(T0.plusSeconds(4));
        assertThat(page.content().get(2).timestamp()).isEqualTo(T0.plusSeconds(2));
        assertThat(small.summary().totalCalls()).isEqualTo(5);
    }

    @Test
    void returnsNewestFirst() {
        store.finished(entry("orders", CallStatus.SUCCESS, 1, T0));
        store.finished(entry("orders", CallStatus.SUCCESS, 2, T0.plusSeconds(1)));

        assertThat(all().content().get(0).durationMs()).isEqualTo(2);
    }

    @Test
    void filtersCombineAndDatesAreInclusive() {
        store.finished(entry("orders", CallStatus.SUCCESS, 1, T0));
        store.finished(entry("orders", CallStatus.ERROR, 2, T0.plusSeconds(10)));
        store.finished(entry("orders", CallStatus.ERROR, 3, T0.plusSeconds(20)));
        store.finished(entry("orders", CallStatus.ERROR, 4, T0.plusSeconds(30)));
        store.finished(entry("payments", CallStatus.ERROR, 5, T0.plusSeconds(20)));

        LogPage page = store.query(new LogQuery("orders", CallStatus.ERROR, T0.plusSeconds(10), T0.plusSeconds(20), 0, 20));

        assertThat(page.content()).extracting(LogEntry::durationMs).containsExactly(3L, 2L);
    }

    @Test
    void paginatesWithTotals() {
        for (int i = 0; i < 25; i++) {
            store.finished(entry("orders", CallStatus.SUCCESS, i, T0.plusSeconds(i)));
        }

        LogPage second = store.query(new LogQuery(null, null, null, null, 1, 10));
        LogPage beyond = store.query(new LogQuery(null, null, null, null, 9, 10));

        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(10);
        assertThat(second.totalElements()).isEqualTo(25);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(second.content()).hasSize(10);
        assertThat(second.content().get(0).durationMs()).isEqualTo(14);
        assertThat(beyond.content()).isEmpty();
    }

    @Test
    void emptyResultHasZeroPages() {
        assertThat(all().totalPages()).isZero();
    }

    @Test
    void concurrentWritesLoseNothing() throws Exception {
        CallLogStore big = new CallLogStore(10_000);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int t = 0; t < 8; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < 1000; i++) {
                        big.finished(entry("inventory", CallStatus.SUCCESS, 1, T0));
                    }
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(big.summary().totalCalls()).isEqualTo(8000);
        assertThat(big.query(new LogQuery(null, null, null, null, 0, 1)).totalElements()).isEqualTo(8000);
    }
}
