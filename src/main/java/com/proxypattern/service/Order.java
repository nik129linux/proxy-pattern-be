package com.proxypattern.service;

public record Order(String id, String sku, int quantity, String customer, OrderStatus status) {
}
