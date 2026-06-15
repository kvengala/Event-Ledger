package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.Transaction;
import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.dto.TransactionResponse;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResult applyTransaction(String accountId, TransactionRequest request) {
        var existing = transactionRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            Transaction transaction = existing.get();
            if (!matchesRequest(transaction, accountId, request)) {
                throw new IllegalArgumentException(
                        "eventId already exists with different transaction details: " + request.eventId()
                );
            }
            return new TransactionResult(toResponse(transaction), false);
        }

        accountRepository.findById(accountId)
                .orElseGet(() -> accountRepository.save(new Account(accountId, request.currency(), Instant.now())));

        Transaction transaction = transactionRepository.save(new Transaction(
                request.eventId(),
                accountId,
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                Instant.now()
        ));

        log.info("Applied transaction {} to account {}", request.eventId(), accountId);
        return new TransactionResult(toResponse(transaction), true);
    }

    private boolean matchesRequest(Transaction transaction, String accountId, TransactionRequest request) {
        return Objects.equals(transaction.getAccountId(), accountId)
                && transaction.getType() == request.type()
                && transaction.getAmount().compareTo(request.amount()) == 0
                && Objects.equals(transaction.getCurrency(), request.currency())
                && Objects.equals(transaction.getEventTimestamp(), request.eventTimestamp());
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return new BalanceResponse(
                accountId,
                transactionRepository.calculateBalance(accountId),
                account.getCurrency()
        );
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<TransactionResponse> transactions = transactionRepository
                .findByAccountIdOrderByEventTimestampDesc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();

        return new AccountResponse(
                accountId,
                transactionRepository.calculateBalance(accountId),
                account.getCurrency(),
                account.getCreatedAt(),
                transactions
        );
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getEventId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getEventTimestamp(),
                transaction.getAppliedAt()
        );
    }

    public record TransactionResult(TransactionResponse transaction, boolean created) {
    }
}
