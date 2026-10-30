package com.proxypattern.metrics;

import com.proxypattern.proxy.MicroserviceProxy;
import com.proxypattern.service.InventoryResult;
import com.proxypattern.service.OrderResult;
import com.proxypattern.service.PaymentResult;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class LoadSimulator {

    private static final int CALLS = 50;
    private static final List<String> SKUS = List.of("SKU-1001", "SKU-1002", "SKU-1003", "SKU-1004");

    private final MicroserviceProxy<InventoryResult> inventory;
    private final MicroserviceProxy<OrderResult> orders;
    private final MicroserviceProxy<PaymentResult> payments;
    private final RandomGenerator random;

    public LoadSimulator(
            MicroserviceProxy<InventoryResult> inventory,
            MicroserviceProxy<OrderResult> orders,
            MicroserviceProxy<PaymentResult> payments,
            RandomGenerator random) {
        this.inventory = inventory;
        this.orders = orders;
        this.payments = payments;
        this.random = random;
    }

    public SimulateLoadResult run() {
        long start = System.currentTimeMillis();
        Queue<String> orderIds = new ConcurrentLinkedQueue<>();
        Queue<String> paymentIds = new ConcurrentLinkedQueue<>();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CALLS; i++) {
                pool.submit(() -> {
                    try {
                        doOne(orderIds, paymentIds);
                        success.incrementAndGet();
                    } catch (RuntimeException e) {
                        error.incrementAndGet();
                    }
                });
            }
        }
        long duration = System.currentTimeMillis() - start;
        return new SimulateLoadResult(CALLS, success.get(), error.get(), duration);
    }

    private void doOne(Queue<String> orderIds, Queue<String> paymentIds) {
        int service = random.nextInt(3);
        if (service == 0) {
            inventoryOne();
        } else if (service == 1) {
            ordersOne(orderIds);
        } else {
            paymentsOne(paymentIds);
        }
    }

    private void inventoryOne() {
        if (random.nextDouble() < 0.05) {
            inventory.execute("check", Map.of("sku", "SKU-UNKNOWN"));
            return;
        }
        String sku = SKUS.get(random.nextInt(SKUS.size()));
        int op = random.nextInt(4);
        switch (op) {
            case 0 -> inventory.execute("list", Map.of());
            case 1 -> inventory.execute("check", Map.of("sku", sku));
            case 2 -> inventory.execute("reserve", Map.of("sku", sku, "quantity", 1));
            default -> inventory.execute("restock", Map.of("sku", sku, "quantity", 2));
        }
    }

    private void ordersOne(Queue<String> orderIds) {
        if (random.nextDouble() < 0.05) {
            orders.execute("get", Map.of("orderId", "ORD-0"));
            return;
        }
        int op = random.nextInt(4);
        String known = op >= 2 ? orderIds.poll() : null;
        if (op == 1) {
            orders.execute("list", Map.of());
        } else if (known == null) {
            String sku = SKUS.get(random.nextInt(SKUS.size()));
            orderIds.add(orders.execute("create", Map.of("sku", sku, "quantity", 1, "customer", "Load test"))
                    .orders().get(0).id());
        } else if (op == 2) {
            orders.execute("get", Map.of("orderId", known));
            orderIds.add(known);
        } else {
            orders.execute("cancel", Map.of("orderId", known));
        }
    }

    private void paymentsOne(Queue<String> paymentIds) {
        if (random.nextDouble() < 0.10) {
            payments.execute("charge", Map.of("orderId", "ORD-1001", "amount", 9000));
            return;
        }
        int op = random.nextInt(3);
        String known = op >= 1 ? paymentIds.poll() : null;
        if (known == null) {
            int amount = 10 + random.nextInt(491);
            paymentIds.add(payments.execute("charge", Map.of("orderId", "ORD-1001", "amount", amount))
                    .payment().id());
        } else if (op == 1) {
            paymentIds.add(known);
            payments.execute("status", Map.of("paymentId", known));
        } else {
            payments.execute("refund", Map.of("paymentId", known));
        }
    }
}
