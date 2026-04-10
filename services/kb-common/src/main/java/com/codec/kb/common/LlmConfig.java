package com.codec.kb.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmConfig(
    String baseUrl,
    String apiKey,
    String model
) {}
