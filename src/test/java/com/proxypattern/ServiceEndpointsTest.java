package com.proxypattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proxypattern.proxy.LoggingProxy;
import com.proxypattern.proxy.MicroserviceProxy;
import com.proxypattern.service.InventoryService;
import com.proxypattern.service.OrderService;
import com.proxypattern.service.PaymentService;
import com.proxypattern.web.ServiceController;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
        "app.latency.enabled=false",
        "app.frontend-origin=https://proxy.example.app"})
@AutoConfigureMockMvc
@Import(ServiceEndpointsTest.NoRandomFailures.class)
class ServiceEndpointsTest {

    @TestConfiguration
    static class NoRandomFailures {
        @Bean
        @Primary
        RandomGenerator neverFails() {
            return new Random() {
                @Override
                public double nextDouble() {
                    return 0.99;
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    private ResultActions call(String path, String body) throws Exception {
        var request = post(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    @Test
    void inventoryOperationWithoutBodyWorks() throws Exception {
        call("/api/services/inventory/list", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    void ordersOperationWorks() throws Exception {
        call("/api/services/orders/create", "{\"sku\":\"SKU-1002\",\"quantity\":1,\"customer\":\"Ana Rojas\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].status").value("CREATED"));
    }

    @Test
    void paymentsOperationWorks() throws Exception {
        call("/api/services/payments/charge", "{\"orderId\":\"ORD-1001\",\"amount\":99.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));
    }

    @Test
    void unknownOperationIs404WithErrorShape() throws Exception {
        call("/api/services/inventory/explode", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("UNKNOWN_OPERATION"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unknownResourceIs404() throws Exception {
        call("/api/services/orders/get", "{\"orderId\":\"ORD-0\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void invalidParamsAre400() throws Exception {
        call("/api/services/inventory/reserve", "{\"sku\":\"SKU-1001\",\"quantity\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMS"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        call("/api/services/inventory/check", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMS"));
    }

    @Test
    void businessRuleIs409() throws Exception {
        call("/api/services/payments/charge", "{\"orderId\":\"ORD-1\",\"amount\":9000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE"));
    }

    @Test
    void oversizedBodyIs413() throws Exception {
        String huge = "{\"sku\":\"" + "x".repeat(17_000) + "\"}";

        call("/api/services/inventory/check", huge)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("BODY_TOO_LARGE"));
    }

    @Test
    void allowsTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/services/inventory/list")
                        .header("Origin", "https://proxy.example.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://proxy.example.app"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/services/inventory/list")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyServiceBeanIsTheLoggingProxy() {
        var beans = context.getBeansOfType(MicroserviceProxy.class);

        assertThat(beans).hasSize(3);
        assertThat(beans.values()).allMatch(LoggingProxy.class::isInstance);
    }

    @Test
    void realServicesAreNeverBeans() {
        assertThat(context.getBeansOfType(InventoryService.class)).isEmpty();
        assertThat(context.getBeansOfType(OrderService.class)).isEmpty();
        assertThat(context.getBeansOfType(PaymentService.class)).isEmpty();
    }

    @Test
    void controllerDependsOnlyOnTheProxyInterface() {
        for (Field field : ServiceController.class.getDeclaredFields()) {
            assertThat(field.getType()).isEqualTo(MicroserviceProxy.class);
        }
    }
}
