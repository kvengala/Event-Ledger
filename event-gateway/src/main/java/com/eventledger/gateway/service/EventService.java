package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.domain.EventEntity;
import com.eventledger.gateway.dto.AccountTransactionRequest;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.exception.EventConflictException;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.metrics.EventMetrics;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    public EventService(
            EventRepository eventRepository,
            AccountServiceClient accountServiceClient,
            ObjectMapper objectMapper,
            EventMetrics eventMetrics
    ) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
        this.eventMetrics = eventMetrics;
    }

    @Transactional
    public SubmitResult submitEvent(EventRequest request) {
        var existing = eventRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            validateDuplicateMatches(existing.get(), request);
            eventMetrics.recordSubmission("POST /events", "duplicate");
            return new SubmitResult(toResponse(existing.get()), false);
        }

        accountServiceClient.applyTransaction(
                request.accountId(),
                new AccountTransactionRequest(
                        request.eventId(),
                        request.type(),
                        request.amount(),
                        request.currency(),
                        request.eventTimestamp()
                )
        );

        EventEntity saved;
        try {
            saved = eventRepository.saveAndFlush(new EventEntity(
                    request.eventId(),
                    request.accountId(),
                    request.type(),
                    request.amount(),
                    request.currency(),
                    request.eventTimestamp(),
                    serializeMetadata(request.metadata()),
                    Instant.now()
            ));
        } catch (DataIntegrityViolationException ex) {
            EventEntity duplicate = eventRepository.findByEventId(request.eventId())
                    .orElseThrow(() -> ex);
            validateDuplicateMatches(duplicate, request);
            eventMetrics.recordSubmission("POST /events", "duplicate");
            return new SubmitResult(toResponse(duplicate), false);
        }

        eventMetrics.recordSubmission("POST /events", "created");
        return new SubmitResult(toResponse(saved), true);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String id) {
        EventEntity event = findEvent(id);
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateDuplicateMatches(EventEntity existing, EventRequest request) {
        if (!matchesRequest(existing, request)) {
            throw new EventConflictException(
                    "eventId already exists with different event details: " + request.eventId()
            );
        }
    }

    private boolean matchesRequest(EventEntity existing, EventRequest request) {
        return Objects.equals(existing.getAccountId(), request.accountId())
                && existing.getType() == request.type()
                && existing.getAmount().compareTo(request.amount()) == 0
                && Objects.equals(existing.getCurrency(), request.currency())
                && Objects.equals(existing.getEventTimestamp(), request.eventTimestamp());
    }

    private EventEntity findEvent(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return eventRepository.findById(uuid)
                    .orElseGet(() -> eventRepository.findByEventId(id)
                            .orElseThrow(() -> new EventNotFoundException(id)));
        } catch (IllegalArgumentException ex) {
            return eventRepository.findByEventId(id)
                    .orElseThrow(() -> new EventNotFoundException(id));
        }
    }

    private EventResponse toResponse(EventEntity entity) {
        return new EventResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getAccountId(),
                entity.getType(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getEventTimestamp(),
                deserializeMetadata(entity.getMetadataJson()),
                entity.getReceivedAt()
        );
    }

    private String serializeMetadata(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("metadata must be valid JSON");
        }
    }

    private JsonNode deserializeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(metadataJson);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public record SubmitResult(EventResponse event, boolean created) {
    }
}
