package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.domain.EventEntity;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.metrics.EventMetrics;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Autowired
    private EventRepository eventRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private EventMetrics eventMetrics;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        eventService = new EventService(eventRepository, accountServiceClient, objectMapper, eventMetrics);
    }

    @Test
    void submitEventPersistsAfterAccountServiceSucceeds() {
        EventRequest request = sampleRequest("evt-001");

        EventService.SubmitResult result = eventService.submitEvent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.event().eventId()).isEqualTo("evt-001");
        assertThat(eventRepository.findAll()).hasSize(1);
        verify(accountServiceClient).applyTransaction(eq("acct-123"), any());
    }

    @Test
    void duplicateEventIdIsIdempotent() {
        EventRequest request = sampleRequest("evt-dup");
        eventService.submitEvent(request);

        EventService.SubmitResult second = eventService.submitEvent(request);

        assertThat(second.created()).isFalse();
        assertThat(eventRepository.findAll()).hasSize(1);
        verify(accountServiceClient).applyTransaction(eq("acct-123"), any());
    }

    @Test
    void accountServiceFailureDoesNotPersistEvent() {
        doThrow(new AccountServiceUnavailableException("down"))
                .when(accountServiceClient)
                .applyTransaction(eq("acct-123"), any());

        assertThatThrownBy(() -> eventService.submitEvent(sampleRequest("evt-fail")))
                .isInstanceOf(AccountServiceUnavailableException.class);

        assertThat(eventRepository.findAll()).isEmpty();
    }

    @Test
    void listEventsByAccountReturnsChronologicalOrder() {
        eventService.submitEvent(sampleRequest("evt-2", "2026-05-15T16:00:00Z"));
        eventService.submitEvent(sampleRequest("evt-1", "2026-05-15T10:00:00Z"));

        List<EventResponse> events = eventService.listEventsByAccount("acct-123");

        assertThat(events).extracting(EventResponse::eventId).containsExactly("evt-1", "evt-2");
    }

    @Test
    void idempotentLookupSkipsAccountServiceCall() {
        EventRequest request = sampleRequest("evt-skip");
        eventRepository.save(new EventEntity(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                null,
                Instant.parse("2026-06-15T00:00:00Z")
        ));

        EventService.SubmitResult result = eventService.submitEvent(request);

        assertThat(result.created()).isFalse();
        verifyNoInteractions(accountServiceClient);
    }

    private EventRequest sampleRequest(String eventId) {
        return sampleRequest(eventId, "2026-05-15T14:02:11Z");
    }

    private EventRequest sampleRequest(String eventId, String timestamp) {
        return new EventRequest(
                eventId,
                "acct-123",
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse(timestamp),
                null
        );
    }
}
