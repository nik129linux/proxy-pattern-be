package com.proxypattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.latency.enabled=false", "app.random-seed=42"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MetricsApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private JsonNode getJson(String path) throws Exception {
        String body = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode postSimulate() throws Exception {
        String body = mvc.perform(post("/api/metrics/simulate-load")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode service(JsonNode summary, String id) {
        for (JsonNode node : summary.get("services")) {
            if (node.get("serviceId").asText().equals(id)) {
                return node;
            }
        }
        throw new AssertionError(id);
    }

    @Test
    void aCallThroughTheEndpointShowsUpInTheLogs() throws Exception {
        mvc.perform(post("/api/services/inventory/check").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-1001\"}")).andExpect(status().isOk());

        JsonNode log = getJson("/api/metrics/logs").get("content").get(0);

        assertThat(log.get("serviceId").asText()).isEqualTo("inventory");
        assertThat(log.get("operation").asText()).isEqualTo("check");
        assertThat(log.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(log.get("durationMs").isNumber()).isTrue();
        assertThat(UUID.fromString(log.get("requestId").asText())).isNotNull();
        assertThat(log.get("timestamp").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(log.get("params").get(0).get("sku").asText()).isEqualTo("SKU-1001");
        assertThat(log.get("response").get("items").get(0).get("sku").asText()).isEqualTo("SKU-1001");
    }

    @Test
    void aFailedCallIsLoggedWithItsErrorSummary() throws Exception {
        mvc.perform(post("/api/services/orders/get").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"ORD-0\"}")).andExpect(status().isNotFound());

        JsonNode log = getJson("/api/metrics/logs?status=ERROR").get("content").get(0);

        assertThat(log.get("serviceId").asText()).isEqualTo("orders");
        assertThat(log.get("errorType").asText()).isEqualTo("ResourceNotFoundException");
        assertThat(log.get("errorMessage").asText()).isNotBlank();
        assertThat(log.get("stackSummary").size()).isBetween(1, 5);
    }

    @Test
    void simulateLoadGeneratesExactlyFiftyCalls() throws Exception {
        JsonNode result = postSimulate();

        assertThat(result.get("generated").asInt()).isEqualTo(50);
        assertThat(result.get("success").asInt() + result.get("error").asInt()).isEqualTo(50);
        assertThat(getJson("/api/metrics/summary").get("totalCalls").asInt()).isEqualTo(50);
        assertThat(getJson("/api/metrics/logs?size=100").get("totalElements").asInt()).isEqualTo(50);
    }

    @Test
    void repeatedLoadKeepsPaymentsAboveTheAlarmAndTheOthersBelow() throws Exception {
        for (int i = 0; i < 40; i++) {
            postSimulate();
        }

        JsonNode summary = getJson("/api/metrics/summary");

        assertThat(summary.get("totalCalls").asInt()).isEqualTo(2000);
        assertThat(service(summary, "payments").get("errorRate").asDouble()).isGreaterThan(0.15);
        assertThat(service(summary, "inventory").get("errorRate").asDouble()).isLessThan(0.09);
        assertThat(service(summary, "orders").get("errorRate").asDouble()).isLessThan(0.09);
    }

    @Test
    void filtersLogsByServiceAndStatus() throws Exception {
        postSimulate();

        for (JsonNode log : getJson("/api/metrics/logs?service=payments&size=100").get("content")) {
            assertThat(log.get("serviceId").asText()).isEqualTo("payments");
        }
        for (JsonNode log : getJson("/api/metrics/logs?status=SUCCESS&size=100").get("content")) {
            assertThat(log.get("status").asText()).isEqualTo("SUCCESS");
        }
    }

    @Test
    void blankFiltersMeanNoFilter() throws Exception {
        postSimulate();

        assertThat(getJson("/api/metrics/logs?service=&status=&from=&to=&size=100").get("totalElements").asInt())
                .isEqualTo(50);
    }

    @Test
    void filtersLogsByDateRange() throws Exception {
        postSimulate();

        assertThat(getJson("/api/metrics/logs?to=2000-01-01T00:00:00Z").get("totalElements").asInt()).isZero();
        assertThat(getJson("/api/metrics/logs?from=2000-01-01T00:00:00Z").get("totalElements").asInt()).isEqualTo(50);
    }

    @Test
    void rejectsBadLogQueries() throws Exception {
        for (String query : new String[] {"service=shipping", "status=MAYBE", "from=yesterday", "to=12",
                "from=2026-10-30T12:00:00Z&to=2026-10-30T11:00:00Z", "page=-1", "size=0", "size=101"}) {
            mvc.perform(get("/api/metrics/logs?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_PARAMS"));
        }
    }

    @Test
    void metricsCallsThemselvesAreNotLogged() throws Exception {
        getJson("/api/metrics/summary");
        getJson("/api/metrics/logs");

        assertThat(getJson("/api/metrics/summary").get("totalCalls").asInt()).isZero();
    }
}
