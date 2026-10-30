package com.proxypattern.service;

public record Payment(String id, String orderId, double amount, PaymentStatus status) {
}
