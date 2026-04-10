package com.codec.kb.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentDto(
    String id,
    String filename,
    String contentType,
    long sizeBytes,
    String sha256,
    String uploadedAt,
    String updatedAt,
    String category,
    List<String> tags
) {}
