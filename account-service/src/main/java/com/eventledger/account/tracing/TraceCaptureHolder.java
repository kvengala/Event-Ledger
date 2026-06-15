package com.eventledger.account.tracing;

import java.util.concurrent.atomic.AtomicReference;

public final class TraceCaptureHolder {

    private static final AtomicReference<String> lastTraceId = new AtomicReference<>();

    private TraceCaptureHolder() {
    }

    public static void record(String traceId) {
        lastTraceId.set(traceId);
    }

    public static String getLastTraceId() {
        return lastTraceId.get();
    }

    public static void clear() {
        lastTraceId.set(null);
    }
}
