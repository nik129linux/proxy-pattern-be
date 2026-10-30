package com.proxypattern.metrics;

import java.time.Instant;

/** Null fields mean "no filter". */
public record LogQuery(String service, CallStatus status, Instant from, Instant to, int page, int size) {
}
