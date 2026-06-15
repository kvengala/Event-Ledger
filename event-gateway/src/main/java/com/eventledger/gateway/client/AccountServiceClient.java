package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.AccountTransactionRequest;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.tracing.TraceContext;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);

    private final RestClient accountRestClient;

    public AccountServiceClient(RestClient accountRestClient) {
        this.accountRestClient = accountRestClient;
    }

    @Retry(name = "accountService", fallbackMethod = "applyTransactionFallback")
    public void applyTransaction(String accountId, AccountTransactionRequest request) {
        executeApplyTransaction(accountId, request);
    }

    @SuppressWarnings("unused")
    private void applyTransactionFallback(
            String accountId,
            AccountTransactionRequest request,
            Exception ex
    ) {
        throw new AccountServiceUnavailableException(
                "Account Service is unavailable while applying transaction " + request.eventId(),
                ex
        );
    }

    private void executeApplyTransaction(String accountId, AccountTransactionRequest request) {
        var requestSpec = accountRestClient.post()
                .uri("/accounts/{accountId}/transactions", accountId);

        String traceId = MDC.get(TraceContext.MDC_TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            requestSpec = requestSpec.header(TraceContext.TRACE_HEADER, traceId);
        }

        requestSpec.body(request)
                .retrieve()
                .toBodilessEntity();
        log.info("Applied transaction {} to account {}", request.eventId(), accountId);
    }
}
