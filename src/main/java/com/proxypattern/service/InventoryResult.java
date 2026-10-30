package com.proxypattern.service;

import java.util.List;

public record InventoryResult(String message, List<StockItem> items) {
}
