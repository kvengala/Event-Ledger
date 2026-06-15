package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.AccountTransactionRequest;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceClientResiliencyTest {

    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.url", wireMockServer::baseUrl);
    }

    @Autowired
    private AccountServiceClient accountServiceClient;

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void retriesExhaustedThenThrowsUnavailableException() {
        wireMockServer.stubFor(post(urlEqualTo("/accounts/acct-123/transactions"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> accountServiceClient.applyTransaction("acct-123", sampleRequest()))
                .isInstanceOf(AccountServiceUnavailableException.class);

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/accounts/acct-123/transactions")));
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
