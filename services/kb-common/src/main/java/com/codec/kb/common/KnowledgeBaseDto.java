package com.codec.kb.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeBaseDto(
    String id,
    String name,
    String embeddingMode,
    String embeddingModel,
    String embeddingBaseUrl,
    String createdAt,
    String updatedAt
) {}
