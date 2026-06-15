package com.eventledger.gateway.integration;

import com.eventledger.account.AccountServiceApplication;
import com.eventledger.account.tracing.TraceCaptureHolder;
import com.eventledger.account.tracing.TraceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EndToEndIntegrationTest {

    private static final ConfigurableApplicationContext accountServiceContext;

    static {
        accountServiceContext = new SpringApplicationBuilder(AccountServiceApplication.class)
                .profiles("test", "integration-test")
                .properties("server.port=0")
                .run();
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerAccountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("account-service.url", EndToEndIntegrationTest::accountServiceBaseUrl);
    }

    @AfterAll
    static void shutdownAccountService() {
        accountServiceContext.close();
    }

    @BeforeEach
    void resetTraceCapture() {
        TraceCaptureHolder.clear();
    }

    @Test
    void submitEventUpdatesAccountBalanceEndToEnd() {
        ResponseEntity<Map> response = submitEvent(
                "evt-e2e-001",
                "acct-e2e-1",
                "CREDIT",
                "150.00",
                "2026-05-15T14:02:11Z",
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> balance = restTemplate.getForEntity(
                accountServiceBaseUrl() + "/accounts/acct-e2e-1/balance",
                Map.class
        );

        assertThat(balance.getBody()).containsEntry("balance", 150.0);
    }

    @Test
    void duplicateSubmissionIsIdempotentAcrossServices() {
        submitEvent("evt-e2e-dup", "acct-e2e-2", "CREDIT", "50.00", "2026-05-15T14:00:00Z", null);

        ResponseEntity<Map> duplicate = submitEvent(
                "evt-e2e-dup",
                "acct-e2e-2",
                "CREDIT",
                "50.00",
                "2026-05-15T14:00:00Z",
                null
        );

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> balance = restTemplate.getForEntity(
                accountServiceBaseUrl() + "/accounts/acct-e2e-2/balance",
                Map.class
        );
        assertThat(balance.getBody()).containsEntry("balance", 50.0);
    }

    @Test
    void duplicateSubmissionWithDifferentDetailsReturnsConflict() {
        submitEvent("evt-e2e-conflict", "acct-e2e-conflict", "CREDIT", "50.00", "2026-05-15T14:00:00Z", null);

        ResponseEntity<Map> conflict = submitEvent(
                "evt-e2e-conflict",
                "acct-e2e-conflict",
                "DEBIT",
                "50.00",
                "2026-05-15T14:00:00Z",
                null
        );

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).containsEntry("status", 409);

        ResponseEntity<Map> balance = restTemplate.getForEntity(
                accountServiceBaseUrl() + "/accounts/acct-e2e-conflict/balance",
                Map.class
        );
        assertThat(balance.getBody()).containsEntry("balance", 50.0);
    }

    @Test
    void outOfOrderEventsProduceCorrectBalanceAndListing() {
        submitEvent("evt-e2e-later", "acct-e2e-3", "CREDIT", "100.00", "2026-05-15T16:00:00Z", null);
        submitEvent("evt-e2e-earlier", "acct-e2e-3", "CREDIT", "25.00", "2026-05-15T10:00:00Z", null);
        submitEvent("evt-e2e-debit", "acct-e2e-3", "DEBIT", "30.00", "2026-05-15T18:00:00Z", null);

        ResponseEntity<Map> balance = restTemplate.getForEntity(
                accountServiceBaseUrl() + "/accounts/acct-e2e-3/balance",
                Map.class
        );
        assertThat(balance.getBody()).containsEntry("balance", 95.0);

        ResponseEntity<List> events = restTemplate.getForEntity(
                gatewayUrl("/events?account=acct-e2e-3"),
                List.class
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> eventList = events.getBody();
        assertThat(eventList).extracting(event -> event.get("eventId"))
                .containsExactly("evt-e2e-earlier", "evt-e2e-later", "evt-e2e-debit");
    }

    @Test
    void traceIdPropagatesFromGatewayToAccountService() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TraceContext.TRACE_HEADER, "e2e-trace-999");

        ResponseEntity<Map> response = restTemplate.exchange(
                gatewayUrl("/events"),
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "eventId": "evt-e2e-trace",
                          "accountId": "acct-e2e-trace",
                          "type": "CREDIT",
                          "amount": 10.00,
                          "currency": "USD",
                          "eventTimestamp": "2026-05-15T14:02:11Z"
                        }
                        """, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(TraceContext.TRACE_HEADER)).isEqualTo("e2e-trace-999");

        ResponseEntity<Map> accountTrace = restTemplate.getForEntity(
                accountServiceBaseUrl() + "/internal/test/last-trace-id",
                Map.class
        );
        assertThat(accountTrace.getBody()).containsEntry("traceId", "e2e-trace-999");
    }

    @Test
    void metricsEndpointRecordsSuccessfulSubmission() {
        submitEvent("evt-e2e-metrics", "acct-e2e-metrics", "CREDIT", "20.00", "2026-05-15T14:02:11Z", null);

        ResponseEntity<String> metrics = restTemplate.getForEntity(
                gatewayUrl("/metrics/events.submitted.total?tag=status:created&tag=endpoint:POST /events"),
                String.class
        );

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("events.submitted.total");
        assertThat(metrics.getBody()).contains("\"value\":");
    }

    private ResponseEntity<Map> submitEvent(
            String eventId,
            String accountId,
            String type,
            String amount,
            String timestamp,
            HttpHeaders extraHeaders
    ) {
        HttpHeaders headers = extraHeaders == null ? new HttpHeaders() : extraHeaders;
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = """
                {
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "%s",
                  "amount": %s,
                  "currency": "USD",
                  "eventTimestamp": "%s"
                }
                """.formatted(eventId, accountId, type, amount, timestamp);

        return restTemplate.exchange(
                gatewayUrl("/events"),
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Map.class
        );
    }

    private String gatewayUrl(String path) {
        return "http://127.0.0.1:" + gatewayPort + path;
    }

    private static String accountServiceBaseUrl() {
        Integer port = accountServiceContext.getEnvironment()
                .getProperty("local.server.port", Integer.class);
        return "http://127.0.0.1:" + port;
    }
}
