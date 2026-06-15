package com.eventledger.account.repository;

import com.eventledger.account.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByEventId(String eventId);

    List<Transaction> findByAccountIdOrderByEventTimestampDesc(String accountId);

    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);

    @Query("""
            select coalesce(sum(
                case when t.type = com.eventledger.account.domain.TransactionType.CREDIT then t.amount
                     when t.type = com.eventledger.account.domain.TransactionType.DEBIT then -t.amount
                     else 0 end
            ), 0)
            from Transaction t
            where t.accountId = :accountId
            """)
    BigDecimal calculateBalance(@Param("accountId") String accountId);
}
