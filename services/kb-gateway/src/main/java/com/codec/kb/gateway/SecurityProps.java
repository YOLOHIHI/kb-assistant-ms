package com.codec.kb.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProps(
    String bootstrapAdminUser,
    String bootstrapAdminPassword
) {}
