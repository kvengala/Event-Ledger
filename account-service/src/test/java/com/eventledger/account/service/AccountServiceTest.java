package com.eventledger.account.service;

import com.eventledger.account.domain.Transaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AccountService.class)
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void calculatesBalanceFromCreditsAndDebits() {
        apply("acct-1", "evt-1", TransactionType.CREDIT, "150.00", "2026-05-15T14:00:00Z");
        apply("acct-1", "evt-2", TransactionType.DEBIT, "50.00", "2026-05-15T15:00:00Z");

        var balance = accountService.getBalance("acct-1");

        assertThat(balance.balance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(balance.currency()).isEqualTo("USD");
    }

    @Test
    void duplicateEventIdIsIdempotent() {
        apply("acct-2", "evt-dup", TransactionType.CREDIT, "25.00", "2026-05-15T14:00:00Z");
        var second = accountService.applyTransaction("acct-2", request("evt-dup", TransactionType.CREDIT, "25.00", "2026-05-15T14:00:00Z"));

        assertThat(second.created()).isFalse();
        assertThat(transactionRepository.findAll()).hasSize(1);
        assertThat(accountService.getBalance("acct-2").balance()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void duplicateEventIdWithDifferentDetailsIsRejected() {
        apply("acct-2", "evt-dup-mismatch", TransactionType.CREDIT, "25.00", "2026-05-15T14:00:00Z");

        assertThatThrownBy(() -> accountService.applyTransaction(
                "acct-2",
                request("evt-dup-mismatch", TransactionType.DEBIT, "25.00", "2026-05-15T14:00:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId already exists with different transaction details");

        assertThat(transactionRepository.findAll()).hasSize(1);
        assertThat(accountService.getBalance("acct-2").balance()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void outOfOrderArrivalProducesCorrectBalanceAndChronologicalListing() {
        apply("acct-3", "evt-later", TransactionType.CREDIT, "100.00", "2026-05-15T16:00:00Z");
        apply("acct-3", "evt-earlier", TransactionType.CREDIT, "50.00", "2026-05-15T10:00:00Z");

        assertThat(accountService.getBalance("acct-3").balance()).isEqualByComparingTo(new BigDecimal("150.00"));

        List<String> orderedEventIds = transactionRepository.findByAccountIdOrderByEventTimestampAsc("acct-3")
                .stream()
                .map(Transaction::getEventId)
                .toList();
        assertThat(orderedEventIds).containsExactly("evt-earlier", "evt-later");
    }

    private void apply(String accountId, String eventId, TransactionType type, String amount, String timestamp) {
        accountService.applyTransaction(accountId, request(eventId, type, amount, timestamp));
    }

    private TransactionRequest request(String eventId, TransactionType type, String amount, String timestamp) {
        return new TransactionRequest(
                eventId,
                type,
                new BigDecimal(amount),
                "USD",
                Instant.parse(timestamp)
        );
    }
}
