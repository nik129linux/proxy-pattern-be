package com.proxypattern.service;

import com.proxypattern.error.BusinessRuleException;
import com.proxypattern.error.ResourceNotFoundException;
import com.proxypattern.error.UnknownOperationException;
import com.proxypattern.proxy.MicroserviceProxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** In memory order service keeping the newest 1000 orders. */
public class OrderService implements MicroserviceProxy<OrderResult> {

    private static final int CAPACITY = 1000;
    private static final int PAGE = 20;

    private final Latency latency;
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private int sequence = 1000;

    public OrderService(RandomGenerator random, Latency latency) {
        this.latency = latency;
    }

    @Override
    public OrderResult execute(String operation, Object... params) {
        Map<String, Object> body = Params.body(params);
        latency.pause(20, 90);
        return switch (operation) {
            case "create" -> create(body);
            case "get" -> get(body);
            case "list" -> list();
            case "cancel" -> cancel(body);
            default -> throw new UnknownOperationException("unknown orders operation: " + operation);
        };
    }

    private synchronized OrderResult create(Map<String, Object> body) {
        String sku = Params.text(body, "sku");
        int quantity = Params.positiveInt(body, "quantity");
        String customer = Params.text(body, "customer");
        Order order = new Order("ORD-" + (++sequence), sku, quantity, customer, OrderStatus.CREATED);
        orders.put(order.id(), order);
        evict();
        return new OrderResult("order created", List.of(order));
    }

    private synchronized OrderResult get(Map<String, Object> body) {
        return new OrderResult("order found", List.of(find(Params.text(body, "orderId"))));
    }

    private synchronized OrderResult list() {
        List<Order> newest = new ArrayList<>(PAGE);
        List<Order> all = new ArrayList<>(orders.values());
        for (int i = all.size() - 1; i >= 0 && newest.size() < PAGE; i--) {
            newest.add(all.get(i));
        }
        return new OrderResult("orders listed", newest);
    }

    private synchronized OrderResult cancel(Map<String, Object> body) {
        Order order = find(Params.text(body, "orderId"));
        if (order.status() == OrderStatus.CANCELLED) {
            throw new BusinessRuleException("order already cancelled: " + order.id());
        }
        Order cancelled = new Order(order.id(), order.sku(), order.quantity(), order.customer(),
                OrderStatus.CANCELLED);
        orders.put(cancelled.id(), cancelled);
        return new OrderResult("order cancelled", List.of(cancelled));
    }

    private Order find(String id) {
        Order order = orders.get(id);
        if (order == null) {
            throw new ResourceNotFoundException("unknown order: " + id);
        }
        return order;
    }

    private void evict() {
        Iterator<String> ids = orders.keySet().iterator();
        while (orders.size() > CAPACITY && ids.hasNext()) {
            ids.next();
            ids.remove();
        }
    }
}
