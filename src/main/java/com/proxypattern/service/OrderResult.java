package com.proxypattern.service;

import java.util.List;

public record OrderResult(String message, List<Order> orders) {
}
