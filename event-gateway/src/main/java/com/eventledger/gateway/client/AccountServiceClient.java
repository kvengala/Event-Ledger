package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.AccountTransactionRequest;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);

    private final RestClient accountRestClient;

    public AccountServiceClient(RestClient accountRestClient) {
        this.accountRestClient = accountRestClient;
    }

    public void applyTransaction(String accountId, AccountTransactionRequest request) {
        try {
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
        } catch (RestClientException ex) {
            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable while applying transaction " + request.eventId(),
                    ex
            );
        }
    }
}
