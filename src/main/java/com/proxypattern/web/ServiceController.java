package com.proxypattern.web;

import com.proxypattern.error.UnknownOperationException;
import com.proxypattern.proxy.MicroserviceProxy;
import com.proxypattern.service.InventoryResult;
import com.proxypattern.service.OrderResult;
import com.proxypattern.service.PaymentResult;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single entry point for the three proxies. */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final MicroserviceProxy<InventoryResult> inventory;
    private final MicroserviceProxy<OrderResult> orders;
    private final MicroserviceProxy<PaymentResult> payments;

    public ServiceController(MicroserviceProxy<InventoryResult> inventory,
            MicroserviceProxy<OrderResult> orders,
            MicroserviceProxy<PaymentResult> payments) {
        this.inventory = inventory;
        this.orders = orders;
        this.payments = payments;
    }

    @PostMapping("/{service}/{operation}")
    public Object call(@PathVariable String service, @PathVariable String operation,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body == null ? Map.of() : body;
        return switch (service) {
            case "inventory" -> inventory.execute(operation, params);
            case "orders" -> orders.execute(operation, params);
            case "payments" -> payments.execute(operation, params);
            default -> throw new UnknownOperationException("unknown service: " + service);
        };
    }
}
