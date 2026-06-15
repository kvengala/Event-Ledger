package com.eventledger.account.controller;

import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.dto.TransactionResponse;
import com.eventledger.account.exception.GlobalExceptionHandler;
import com.eventledger.account.service.AccountService;
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

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void applyTransactionReturnsCreatedForNewEvent() throws Exception {
        TransactionRequest request = new TransactionRequest(
                "evt-001",
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z")
        );
        TransactionResponse response = new TransactionResponse(
                "evt-001",
                "acct-123",
                TransactionType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Instant.parse("2026-06-15T00:00:00Z")
        );
        when(accountService.applyTransaction(eq("acct-123"), any(TransactionRequest.class)))
                .thenReturn(new AccountService.TransactionResult(response, true));

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"));
    }

    @Test
    void getBalanceReturnsBalance() throws Exception {
        when(accountService.getBalance("acct-123"))
                .thenReturn(new BalanceResponse("acct-123", new BigDecimal("100.00"), "USD"));

        mockMvc.perform(get("/accounts/acct-123/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }
}
