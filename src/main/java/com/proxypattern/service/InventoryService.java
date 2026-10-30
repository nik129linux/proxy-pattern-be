package com.proxypattern.service;

import com.proxypattern.error.BusinessRuleException;
import com.proxypattern.error.ResourceNotFoundException;
import com.proxypattern.error.UnknownOperationException;
import com.proxypattern.proxy.MicroserviceProxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** In memory stock service. */
public class InventoryService implements MicroserviceProxy<InventoryResult> {

    private final Latency latency;
    private final Map<String, StockItem> items = new LinkedHashMap<>();

    public InventoryService(RandomGenerator random, Latency latency) {
        this.latency = latency;
        items.put("SKU-1001", new StockItem("SKU-1001", "Mechanical keyboard", 40));
        items.put("SKU-1002", new StockItem("SKU-1002", "USB-C dock", 25));
        items.put("SKU-1003", new StockItem("SKU-1003", "27-inch monitor", 12));
        items.put("SKU-1004", new StockItem("SKU-1004", "Laptop stand", 60));
    }

    @Override
    public InventoryResult execute(String operation, Object... params) {
        Map<String, Object> body = Params.body(params);
        latency.pause(10, 60);
        return switch (operation) {
            case "list" -> list();
            case "check" -> check(body);
            case "reserve" -> reserve(body);
            case "restock" -> restock(body);
            default -> throw new UnknownOperationException("unknown inventory operation: " + operation);
        };
    }

    private synchronized InventoryResult list() {
        return new InventoryResult("inventory listed", List.copyOf(items.values()));
    }

    private synchronized InventoryResult check(Map<String, Object> body) {
        StockItem item = find(Params.text(body, "sku"));
        return new InventoryResult("item found", List.of(item));
    }

    private synchronized InventoryResult reserve(Map<String, Object> body) {
        String sku = Params.text(body, "sku");
        int quantity = Params.positiveInt(body, "quantity");
        StockItem item = find(sku);
        if (item.available() < quantity) {
            throw new BusinessRuleException("not enough stock for " + sku);
        }
        return new InventoryResult("reserved " + quantity + " of " + sku,
                List.of(update(item, item.available() - quantity)));
    }

    private synchronized InventoryResult restock(Map<String, Object> body) {
        String sku = Params.text(body, "sku");
        int quantity = Params.positiveInt(body, "quantity");
        StockItem item = find(sku);
        return new InventoryResult("restocked " + quantity + " of " + sku,
                List.of(update(item, item.available() + quantity)));
    }

    private StockItem update(StockItem item, int available) {
        StockItem updated = new StockItem(item.sku(), item.name(), available);
        items.put(item.sku(), updated);
        return updated;
    }

    private StockItem find(String sku) {
        StockItem item = items.get(sku);
        if (item == null) {
            throw new ResourceNotFoundException("unknown sku: " + sku);
        }
        return item;
    }
}
