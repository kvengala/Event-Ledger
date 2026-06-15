package com.eventledger.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account-service")
public record AccountServiceProperties(String url) {
}
