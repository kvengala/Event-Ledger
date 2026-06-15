package com.eventledger.gateway.tracing;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceFilterTest {

    private final TraceFilter traceFilter = new TraceFilter();

    @Test
    void generatesTraceIdWhenHeaderMissing() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isNotBlank();
        });

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).isNotBlank();
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isNull();
    }

    @Test
    void reusesIncomingTraceIdHeader() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        request.addHeader(TraceContext.TRACE_HEADER, "incoming-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isEqualTo("incoming-trace-id"));

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).isEqualTo("incoming-trace-id");
    }
}
