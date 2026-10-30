package com.proxypattern.metrics;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CallLogStore implements CallLog {

    private static final List<String> SERVICES = List.of("inventory", "orders", "payments");

    private final Logger log = LoggerFactory.getLogger(CallLogStore.class);
    private final int capacity;
    private final Deque<LogEntry> entries = new ArrayDeque<>();
    private long totalCalls;
    private long errorCount;
    private long totalDuration;
    private final Map<String, Stats> stats = new HashMap<>();

    public CallLogStore(@Value("${app.log-capacity:10000}") int capacity) {
        this.capacity = capacity;
        for (String s : SERVICES) {
            stats.put(s, new Stats());
        }
    }

    private static class Stats {
        long total;
        long success;
        long error;
        long duration;
    }

    @Override
    public synchronized void started(String requestId, String serviceId, String operation, Instant timestamp) {
        log.info("started {} {} {}", serviceId, operation, requestId);
    }

    @Override
    public synchronized void finished(LogEntry entry) {
        totalCalls++;
        totalDuration += entry.durationMs();
        Stats s = stats.computeIfAbsent(entry.serviceId(), k -> new Stats());
        s.total++;
        s.duration += entry.durationMs();
        if (entry.status() == CallStatus.ERROR) {
            errorCount++;
            s.error++;
        } else {
            s.success++;
        }
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    public synchronized MetricsSummary summary() {
        List<ServiceMetrics> list = new ArrayList<>();
        for (String id : SERVICES) {
            Stats s = stats.getOrDefault(id, new Stats());
            double successRate = s.total == 0 ? 0 : (double) s.success / s.total;
            double errorRate = s.total == 0 ? 0 : (double) s.error / s.total;
            double avg = s.total == 0 ? 0 : (double) s.duration / s.total;
            list.add(new ServiceMetrics(id, s.total, s.success, s.error, successRate, errorRate, avg));
        }
        double overallError = totalCalls == 0 ? 0 : (double) errorCount / totalCalls;
        double overallAvg = totalCalls == 0 ? 0 : (double) totalDuration / totalCalls;
        return new MetricsSummary(totalCalls, overallError, overallAvg, list);
    }

    public synchronized LogPage query(LogQuery query) {
        List<LogEntry> filtered = new ArrayList<>();
        Iterator<LogEntry> it = entries.descendingIterator();
        while (it.hasNext()) {
            LogEntry e = it.next();
            if (query.service() != null && !query.service().equals(e.serviceId())) {
                continue;
            }
            if (query.status() != null && query.status() != e.status()) {
                continue;
            }
            if (query.from() != null && e.timestamp().isBefore(query.from())) {
                continue;
            }
            if (query.to() != null && e.timestamp().isAfter(query.to())) {
                continue;
            }
            filtered.add(e);
        }
        filtered.sort(Comparator.comparing(LogEntry::timestamp).reversed());
        long total = filtered.size();
        int size = query.size();
        int totalPages = total == 0 || size <= 0 ? 0 : (int) ((total + size - 1) / size);
        int fromIndex = query.page() * size;
        List<LogEntry> content;
        if (size <= 0 || fromIndex >= filtered.size()) {
            content = List.of();
        } else {
            content = List.copyOf(filtered.subList(fromIndex, Math.min(fromIndex + size, filtered.size())));
        }
        return new LogPage(content, query.page(), size, total, totalPages);
    }
}
