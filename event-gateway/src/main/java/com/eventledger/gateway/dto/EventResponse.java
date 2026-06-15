package com.eventledger.gateway.dto;

import com.eventledger.gateway.domain.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        JsonNode metadata,
        Instant receivedAt
) {
}
