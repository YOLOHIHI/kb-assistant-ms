package com.codec.kb.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kb")
public record IndexServiceConfig(
    String internalToken,
    String gatewayUrl,
    String embedderUrl,
    String embedApiBaseUrl,
    String embedApiKey
) {}
