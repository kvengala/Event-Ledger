package com.eventledger.gateway.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventMetricsTest {

    @Test
    void recordsSubmissionCounterWithTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EventMetrics metrics = new EventMetrics(registry);

        metrics.recordSubmission("POST /events", "created");
        metrics.recordSubmission("POST /events", "created");
        metrics.recordSubmission("POST /events", "duplicate");

        assertThat(registry.find("events.submitted.total")
                .tag("endpoint", "POST /events")
                .tag("status", "created")
                .counter()
                .count()).isEqualTo(2.0);
        assertThat(registry.find("events.submitted.total")
                .tag("status", "duplicate")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
