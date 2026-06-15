package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.AccountTransactionRequest;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.tracing.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountServiceClientTest {

    private MockRestServiceServer server;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountServiceClient(builder.build());
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void applyTransactionCallsAccountService() {
        server.expect(requestTo("http://localhost:8081/accounts/acct-123/transactions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertThatCode(() -> client.applyTransaction("acct-123", sampleRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void applyTransactionThrowsWhenAccountServiceFails() {
        server.expect(requestTo("http://localhost:8081/accounts/acct-123/transactions"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.applyTransaction("acct-123", sampleRequest()))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    void applyTransactionPropagatesTraceIdHeader() {
        MDC.put(TraceContext.MDC_TRACE_ID, "trace-abc-123");
        try {
            server.expect(requestTo("http://localhost:8081/accounts/acct-123/transactions"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(TraceContext.TRACE_HEADER, "trace-abc-123"))
                    .andRespond(withSuccess());

            assertThatCode(() -> client.applyTransaction("acct-123", sampleRequest()))
                    .doesNotThrowAnyException();
        } finally {
            MDC.remove(TraceContext.MDC_TRACE_ID);
        }
    }

    private AccountTransactionRequest sampleRequest() {
        return new AccountTransactionRequest(
                "evt-001",
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z")
        );
    }
}
