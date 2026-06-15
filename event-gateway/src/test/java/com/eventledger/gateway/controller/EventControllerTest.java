package com.eventledger.gateway.controller;

import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.exception.GlobalExceptionHandler;
import com.eventledger.gateway.metrics.EventMetrics;
import com.eventledger.gateway.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private EventService eventService;

    @Mock
    private EventMetrics eventMetrics;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new EventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler(eventMetrics))
                .setValidator(validator)
                .build();
    }

    @Test
    void submitEventReturnsCreated() throws Exception {
        EventRequest request = new EventRequest(
                "evt-001",
                "acct-123",
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                null
        );
        EventResponse response = new EventResponse(
                UUID.randomUUID(),
                "evt-001",
                "acct-123",
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                null,
                Instant.parse("2026-06-15T00:00:00Z")
        );
        when(eventService.submitEvent(any(EventRequest.class)))
                .thenReturn(new EventService.SubmitResult(response, true));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"));
    }

    @Test
    void submitEventRejectsInvalidAmount() throws Exception {
        String payload = """
                {
                  "eventId": "evt-bad",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 0,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void listEventsByAccountReturnsOrderedResults() throws Exception {
        EventResponse first = new EventResponse(
                UUID.randomUUID(), "evt-1", "acct-123", TransactionType.CREDIT,
                new BigDecimal("10.00"), "USD", Instant.parse("2026-05-15T10:00:00Z"), null,
                Instant.parse("2026-06-15T00:00:00Z")
        );
        when(eventService.listEventsByAccount("acct-123")).thenReturn(List.of(first));

        mockMvc.perform(get("/events").param("account", "acct-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-1"));
    }

    @Test
    void getEventByIdReturnsEvent() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.getEvent(id.toString())).thenReturn(new EventResponse(
                id, "evt-001", "acct-123", TransactionType.CREDIT,
                new BigDecimal("150.00"), "USD", Instant.parse("2026-05-15T14:02:11Z"), null,
                Instant.parse("2026-06-15T00:00:00Z")
        ));

        mockMvc.perform(get("/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-001"));
    }
}
