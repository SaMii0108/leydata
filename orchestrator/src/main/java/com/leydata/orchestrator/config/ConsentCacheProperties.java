package com.leydata.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("leydata.consent")
public record ConsentCacheProperties(
        long cacheTtlSeconds,
        long cacheSoftTtlSeconds
) {
    public ConsentCacheProperties {
        if (cacheTtlSeconds <= 0)     cacheTtlSeconds     = 300;
        if (cacheSoftTtlSeconds <= 0) cacheSoftTtlSeconds = 240;
    }
}
