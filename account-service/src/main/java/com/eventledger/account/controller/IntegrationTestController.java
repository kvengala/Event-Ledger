package com.eventledger.account.controller;

import com.eventledger.account.tracing.TraceCaptureHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("integration-test")
@RequestMapping("/internal/test")
public class IntegrationTestController {

    @GetMapping("/last-trace-id")
    public Map<String, String> lastTraceId() {
        String traceId = TraceCaptureHolder.getLastTraceId();
        return Map.of("traceId", traceId == null ? "" : traceId);
    }
}
