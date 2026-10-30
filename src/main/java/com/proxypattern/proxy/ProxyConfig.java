package com.proxypattern.proxy;

import com.proxypattern.metrics.CallLog;
import com.proxypattern.service.InventoryResult;
import com.proxypattern.service.InventoryService;
import com.proxypattern.service.Latency;
import com.proxypattern.service.OrderResult;
import com.proxypattern.service.OrderService;
import com.proxypattern.service.PaymentResult;
import com.proxypattern.service.PaymentService;
import com.proxypattern.service.RandomLatency;
import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the real services behind their logging proxies. */
@Configuration
public class ProxyConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RandomGenerator randomGenerator(@Value("${app.random-seed:}") String seed) {
        return seed.isBlank() ? new Random() : new Random(Long.parseLong(seed.trim()));
    }

    @Bean
    public Latency latency(RandomGenerator randomGenerator,
            @Value("${app.latency.enabled:true}") boolean enabled) {
        return enabled ? new RandomLatency(randomGenerator) : Latency.none();
    }

    @Bean
    public MicroserviceProxy<InventoryResult> inventoryProxy(RandomGenerator randomGenerator,
            Latency latency, CallLog callLog, Clock clock) {
        return new LoggingProxy<>("inventory", new InventoryService(randomGenerator, latency), callLog, clock);
    }

    @Bean
    public MicroserviceProxy<OrderResult> ordersProxy(RandomGenerator randomGenerator,
            Latency latency, CallLog callLog, Clock clock) {
        return new LoggingProxy<>("orders", new OrderService(randomGenerator, latency), callLog, clock);
    }

    @Bean
    public MicroserviceProxy<PaymentResult> paymentsProxy(RandomGenerator randomGenerator,
            Latency latency, CallLog callLog, Clock clock) {
        return new LoggingProxy<>("payments", new PaymentService(randomGenerator, latency), callLog, clock);
    }
}
