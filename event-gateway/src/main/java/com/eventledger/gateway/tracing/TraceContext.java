package com.eventledger.gateway.tracing;

public final class TraceContext {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";

    private TraceContext() {
    }
}
