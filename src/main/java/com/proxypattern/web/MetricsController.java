package com.proxypattern.web;

import com.proxypattern.error.InvalidParamsException;
import com.proxypattern.metrics.CallLogStore;
import com.proxypattern.metrics.CallStatus;
import com.proxypattern.metrics.LoadSimulator;
import com.proxypattern.metrics.LogPage;
import com.proxypattern.metrics.LogQuery;
import com.proxypattern.metrics.MetricsSummary;
import com.proxypattern.metrics.SimulateLoadResult;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final Set<String> SERVICES = Set.of("inventory", "orders", "payments");

    private static final int MAX_PAGE_SIZE = 100;

    private final CallLogStore store;
    private final LoadSimulator simulator;

    public MetricsController(CallLogStore store, LoadSimulator simulator) {
        this.store = store;
        this.simulator = simulator;
    }

    @GetMapping("/summary")
    public MetricsSummary summary() {
        return store.summary();
    }

    @GetMapping("/logs")
    public LogPage logs(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        String serviceFilter = isBlank(service) ? null : knownService(service);
        CallStatus statusFilter = isBlank(status) ? null : parseStatus(status);
        Instant fromInstant = isBlank(from) ? null : parseInstant("from", from);
        Instant toInstant = isBlank(to) ? null : parseInstant("to", to);
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new InvalidParamsException("from must not be after to");
        }
        int pageNumber = parseInt("page", page);
        int pageSize = parseInt("size", size);
        if (pageNumber < 0) {
            throw new InvalidParamsException("Invalid page: " + page);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new InvalidParamsException("Invalid size: " + size);
        }
        return store.query(new LogQuery(serviceFilter, statusFilter, fromInstant, toInstant, pageNumber, pageSize));
    }

    @PostMapping("/simulate-load")
    public SimulateLoadResult simulateLoad() {
        return simulator.run();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String knownService(String service) {
        if (!SERVICES.contains(service)) {
            throw new InvalidParamsException("Unknown service: " + service);
        }
        return service;
    }

    private static CallStatus parseStatus(String status) {
        try {
            return CallStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new InvalidParamsException("Unknown status: " + status);
        }
    }

    private static Instant parseInstant(String name, String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidParamsException("Invalid " + name + ": " + value);
        }
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidParamsException("Invalid " + name + ": " + value);
        }
    }
}
