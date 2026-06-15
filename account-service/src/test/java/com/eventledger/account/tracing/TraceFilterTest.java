package com.eventledger.account.tracing;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceFilterTest {

    private final TraceFilter traceFilter = new TraceFilter();

    @Test
    void propagatesTraceIdIntoMdcAndResponse() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts/acct-1/transactions");
        request.addHeader(TraceContext.TRACE_HEADER, "trace-from-gateway");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isEqualTo("trace-from-gateway"));

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).isEqualTo("trace-from-gateway");
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isNull();
    }
}
