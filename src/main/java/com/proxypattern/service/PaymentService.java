package com.proxypattern.service;

import com.proxypattern.error.BusinessRuleException;
import com.proxypattern.error.ResourceNotFoundException;
import com.proxypattern.error.SimulatedFailureException;
import com.proxypattern.error.UnknownOperationException;
import com.proxypattern.proxy.MicroserviceProxy;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** In memory payment service keeping the newest 1000 payments. */
public class PaymentService implements MicroserviceProxy<PaymentResult> {

    private static final int CAPACITY = 1000;
    private static final double LIMIT = 5000;

    private final RandomGenerator random;
    private final Latency latency;
    private final Map<String, Payment> payments = new LinkedHashMap<>();
    private int sequence = 1000;

    public PaymentService(RandomGenerator random, Latency latency) {
        this.random = random;
        this.latency = latency;
    }

    @Override
    public PaymentResult execute(String operation, Object... params) {
        Map<String, Object> body = Params.body(params);
        if (random.nextDouble() < 0.10) {
            throw new SimulatedFailureException("payment gateway unavailable");
        }
        latency.pause(40, 180);
        return switch (operation) {
            case "charge" -> charge(body);
            case "status" -> status(body);
            case "refund" -> refund(body);
            default -> throw new UnknownOperationException("unknown payments operation: " + operation);
        };
    }

    private synchronized PaymentResult charge(Map<String, Object> body) {
        String orderId = Params.text(body, "orderId");
        double amount = Params.positiveDouble(body, "amount");
        if (amount > LIMIT) {
            throw new BusinessRuleException("charge declined above " + LIMIT);
        }
        Payment payment = new Payment("PAY-" + (++sequence), orderId, amount, PaymentStatus.CAPTURED);
        payments.put(payment.id(), payment);
        evict();
        return new PaymentResult("payment captured", payment);
    }

    private synchronized PaymentResult status(Map<String, Object> body) {
        return new PaymentResult("payment found", find(Params.text(body, "paymentId")));
    }

    private synchronized PaymentResult refund(Map<String, Object> body) {
        Payment payment = find(Params.text(body, "paymentId"));
        if (payment.status() == PaymentStatus.REFUNDED) {
            throw new BusinessRuleException("payment already refunded: " + payment.id());
        }
        Payment refunded = new Payment(payment.id(), payment.orderId(), payment.amount(),
                PaymentStatus.REFUNDED);
        payments.put(refunded.id(), refunded);
        return new PaymentResult("payment refunded", refunded);
    }

    private Payment find(String id) {
        Payment payment = payments.get(id);
        if (payment == null) {
            throw new ResourceNotFoundException("unknown payment: " + id);
        }
        return payment;
    }

    private void evict() {
        Iterator<String> ids = payments.keySet().iterator();
        while (payments.size() > CAPACITY && ids.hasNext()) {
            ids.next();
            ids.remove();
        }
    }
}
