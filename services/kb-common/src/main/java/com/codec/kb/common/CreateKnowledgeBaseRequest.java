package com.codec.kb.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateKnowledgeBaseRequest(
    String name,
    String embeddingMode,
    String embeddingModel,
    String embeddingBaseUrl
) {}
