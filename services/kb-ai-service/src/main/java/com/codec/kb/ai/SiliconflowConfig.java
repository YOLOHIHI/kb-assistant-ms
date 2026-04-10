package com.codec.kb.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code siliconflow.*} properties that configure the SiliconFlow LLM provider
 * as a fallback when no explicit LLM config is supplied with a chat request.
 */
@ConfigurationProperties(prefix = "siliconflow")
public record SiliconflowConfig(
    String baseUrl,
    String apiKey,
    String model
) {}
