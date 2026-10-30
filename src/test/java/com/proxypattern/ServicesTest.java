package com.proxypattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.proxypattern.error.BusinessRuleException;
import com.proxypattern.error.InvalidParamsException;
import com.proxypattern.error.ResourceNotFoundException;
import com.proxypattern.error.SimulatedFailureException;
import com.proxypattern.error.UnknownOperationException;
import com.proxypattern.service.InventoryService;
import com.proxypattern.service.Latency;
import com.proxypattern.service.OrderResult;
import com.proxypattern.service.OrderService;
import com.proxypattern.service.OrderStatus;
import com.proxypattern.service.PaymentResult;
import com.proxypattern.service.PaymentService;
import com.proxypattern.service.PaymentStatus;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ServicesTest {

    private static final Random NEVER_FAILS = new Random() {
        @Override
        public double nextDouble() {
            return 0.99;
        }
    };

    private final InventoryService inventory = new InventoryService(new Random(1), Latency.none());
    private final OrderService orders = new OrderService(new Random(1), Latency.none());
    private final PaymentService payments = new PaymentService(NEVER_FAILS, Latency.none());

    private int stock(String sku) {
        return inventory.execute("check", Map.of("sku", sku)).items().get(0).available();
    }

    @Test
    void inventoryListsSeededItemsInSkuOrder() {
        var items = inventory.execute("list", Map.of()).items();

        assertThat(items).hasSize(4);
        assertThat(items.get(0).sku()).isEqualTo("SKU-1001");
        assertThat(items.get(0).available()).isEqualTo(40);
        assertThat(items.get(3).sku()).isEqualTo("SKU-1004");
    }

    @Test
    void reserveSubtractsAndRestockAdds() {
        inventory.execute("reserve", Map.of("sku", "SKU-1003", "quantity", 2));
        assertThat(stock("SKU-1003")).isEqualTo(10);

        inventory.execute("restock", Map.of("sku", "SKU-1003", "quantity", 5.0));
        assertThat(stock("SKU-1003")).isEqualTo(15);
    }

    @Test
    void reserveAboveStockFailsAndLeavesStockUntouched() {
        assertThatThrownBy(() -> inventory.execute("reserve", Map.of("sku", "SKU-1003", "quantity", 13)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(stock("SKU-1003")).isEqualTo(12);
    }

    @Test
    void inventoryRejectsBadInput() {
        assertThatThrownBy(() -> inventory.execute("check", Map.of("sku", "NOPE")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> inventory.execute("reserve", Map.of("sku", "SKU-1001", "quantity", 0)))
                .isInstanceOf(InvalidParamsException.class);
        assertThatThrownBy(() -> inventory.execute("reserve", Map.of("sku", "SKU-1001", "quantity", "many")))
                .isInstanceOf(InvalidParamsException.class);
        assertThatThrownBy(() -> inventory.execute("check", Map.of()))
                .isInstanceOf(InvalidParamsException.class);
        assertThatThrownBy(() -> inventory.execute("explode", Map.of()))
                .isInstanceOf(UnknownOperationException.class);
    }

    private OrderResult create() {
        return orders.execute("create", Map.of("sku", "SKU-1001", "quantity", 1, "customer", "Ana Rojas"));
    }

    @Test
    void createsOrdersWithSequentialIds() {
        assertThat(create().orders().get(0).id()).isEqualTo("ORD-1001");
        var second = create().orders().get(0);
        assertThat(second.id()).isEqualTo("ORD-1002");
        assertThat(second.status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void getsAndCancelsAnOrderOnce() {
        String id = create().orders().get(0).id();

        var cancelled = orders.execute("cancel", Map.of("orderId", id)).orders().get(0);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orders.execute("get", Map.of("orderId", id)).orders().get(0).status())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orders.execute("cancel", Map.of("orderId", id)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void orderListShowsNewestTwentyAndOldOrdersAreEvicted() {
        for (int i = 0; i < 1005; i++) {
            create();
        }

        var listed = orders.execute("list", Map.of()).orders();

        assertThat(listed).hasSize(20);
        assertThat(listed.get(0).id()).isEqualTo("ORD-2005");
        assertThat(orders.execute("get", Map.of("orderId", "ORD-1006")).orders()).hasSize(1);
        assertThatThrownBy(() -> orders.execute("get", Map.of("orderId", "ORD-1001")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void orderRejectsBadInput() {
        assertThatThrownBy(() -> orders.execute("create", Map.of("sku", "SKU-1001", "quantity", 1, "customer", " ")))
                .isInstanceOf(InvalidParamsException.class);
        assertThatThrownBy(() -> orders.execute("get", Map.of("orderId", "ORD-0")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orders.execute("ship", Map.of()))
                .isInstanceOf(UnknownOperationException.class);
    }

    private PaymentResult charge(double amount) {
        return payments.execute("charge", Map.of("orderId", "ORD-1001", "amount", amount));
    }

    @Test
    void chargesAndRefundsOnce() {
        var payment = charge(120.5).payment();
        assertThat(payment.id()).isEqualTo("PAY-1001");
        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);

        var refunded = payments.execute("refund", Map.of("paymentId", payment.id())).payment();
        assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payments.execute("status", Map.of("paymentId", payment.id())).payment().status())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> payments.execute("refund", Map.of("paymentId", payment.id())))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void declinesChargesAboveTheLimit() {
        assertThat(charge(5000).payment().amount()).isEqualTo(5000);
        assertThatThrownBy(() -> charge(5000.01)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void paymentRejectsBadInput() {
        assertThatThrownBy(() -> charge(0)).isInstanceOf(InvalidParamsException.class);
        assertThatThrownBy(() -> payments.execute("status", Map.of("paymentId", "PAY-0")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> payments.execute("mine", Map.of()))
                .isInstanceOf(UnknownOperationException.class);
    }

    @Test
    void tenPercentOfPaymentCallsFailOnPurpose() {
        PaymentService seeded = new PaymentService(new Random(42), Latency.none());
        int failures = 0;

        for (int i = 0; i < 10_000; i++) {
            try {
                seeded.execute("charge", Map.of("orderId", "ORD-1", "amount", 10));
            } catch (SimulatedFailureException e) {
                failures++;
            }
        }

        assertThat(failures).isBetween(900, 1100);
    }

    @Test
    void randomFailureAlsoAppliesToOperationsThatAreNotCharge() {
        Random alwaysFails = new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
        PaymentService failing = new PaymentService(alwaysFails, Latency.none());

        assertThatThrownBy(() -> failing.execute("status", Map.of("paymentId", "PAY-1")))
                .isInstanceOf(SimulatedFailureException.class);
    }

    @Test
    void fixedPointOfTheFailureRateIsTenPercent() {
        Random justAbove = new Random() {
            @Override
            public double nextDouble() {
                return 0.10;
            }
        };

        var result = new PaymentService(justAbove, Latency.none())
                .execute("charge", Map.of("orderId", "ORD-1", "amount", 10));
        assertThat(result.payment()).isNotNull();
    }
}
