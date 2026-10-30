package com.proxypattern.metrics;

import java.util.List;

public record LogPage(List<LogEntry> content, int page, int size, long totalElements, int totalPages) {
}
