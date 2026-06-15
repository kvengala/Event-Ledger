package com.eventledger.gateway.dto;

import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionRequest(
        String eventId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp
) {
}
