package com.eventledger.gateway.resiliency;

import com.eventledger.gateway.domain.EventEntity;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.repository.EventRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResiliencyIntegrationTest {

    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.url", wireMockServer::baseUrl);
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void postEventsReturns503WhenAccountServiceUnavailableAfterRetries() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-123/transactions"))
                .willReturn(aResponse().withStatus(500)));

        String payload = """
                {
                  "eventId": "evt-retry-fail",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/accounts/acct-123/transactions")));
        assertThat(eventRepository.findByEventId("evt-retry-fail")).isEmpty();
    }

    @Test
    void postEventsSucceedsAfterTransientAccountServiceFailures() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-456/transactions"))
                .inScenario("transient")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-failure"));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-456/transactions"))
                .inScenario("transient")
                .whenScenarioStateIs("second-failure")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-456/transactions"))
                .inScenario("transient")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(201)));

        String payload = """
                {
                  "eventId": "evt-retry-success",
                  "accountId": "acct-456",
                  "type": "CREDIT",
                  "amount": 75.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-retry-success"));

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/accounts/acct-456/transactions")));
        assertThat(eventRepository.findByEventId("evt-retry-success")).isPresent();
    }

    @Test
    void getEventsStillWorksWhenAccountServiceIsDown() throws Exception {
        eventRepository.save(new EventEntity(
                "evt-stored",
                "acct-789",
                TransactionType.DEBIT,
                new BigDecimal("25.00"),
                "USD",
                Instant.parse("2026-05-15T12:00:00Z"),
                null,
                Instant.parse("2026-06-15T00:00:00Z")
        ));

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-789/transactions"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(get("/events").param("account", "acct-789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-stored"));

        mockMvc.perform(get("/events/evt-stored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-stored"));
    }
}
