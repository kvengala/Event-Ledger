package com.eventledger.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventMetrics {

    private final MeterRegistry meterRegistry;

    public EventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSubmission(String endpoint, String status) {
        Counter.builder("events.submitted.total")
                .description("Total number of event submissions")
                .tag("endpoint", endpoint)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }
}
