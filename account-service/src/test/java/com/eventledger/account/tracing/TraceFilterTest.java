package com.eventledger.account.tracing;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceFilterTest {

    @Mock
    private Environment environment;

    @Test
    void propagatesTraceIdIntoMdcAndResponse() throws ServletException, java.io.IOException {
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        TraceFilter traceFilter = new TraceFilter(environment);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts/acct-1/transactions");
        request.addHeader(TraceContext.TRACE_HEADER, "trace-from-gateway");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isEqualTo("trace-from-gateway"));

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).isEqualTo("trace-from-gateway");
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isNull();
    }

    @Test
    void recordsTraceIdWhenIntegrationTestProfileActive() throws ServletException, java.io.IOException {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"integration-test"});
        TraceFilter traceFilter = new TraceFilter(environment);
        TraceCaptureHolder.clear();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts/acct-1/transactions");
        request.addHeader(TraceContext.TRACE_HEADER, "captured-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, (req, res) -> {
        });

        assertThat(TraceCaptureHolder.getLastTraceId()).isEqualTo("captured-trace");
    }
}
