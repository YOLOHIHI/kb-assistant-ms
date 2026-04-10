package com.codec.kb.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kb")
public record GatewayConfig(
    String internalToken,
    String aiUrl,
    String docUrl,
    String indexUrl,
    boolean allowInsecureDefaults
) {}
