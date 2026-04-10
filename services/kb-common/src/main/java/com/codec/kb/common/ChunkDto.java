package com.codec.kb.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChunkDto(
    String id,
    String docId,
    int index,
    String text,
    String sourceHint
) {}
