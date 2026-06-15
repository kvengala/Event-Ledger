package com.eventledger.account.controller;

import com.eventledger.account.exception.GlobalExceptionHandler;
import com.eventledger.account.service.AccountNotFoundException;
import com.eventledger.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerValidationTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void rejectsInvalidTransactionPayload() throws Exception {
        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "",
                                  "type": "CREDIT",
                                  "amount": 0,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.eventId").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void returnsNotFoundForMissingAccountBalance() throws Exception {
        when(accountService.getBalance("missing")).thenThrow(new AccountNotFoundException("missing"));

        mockMvc.perform(get("/accounts/missing/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found: missing"));
    }
}
