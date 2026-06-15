package com.eventledger.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String accountId, String currency, Instant createdAt) {
        this.accountId = accountId;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
