package com.codec.kb.common;

import java.util.List;

public record ManagedEmbeddingRequest(
    String modelRef,
    List<String> texts
) {}
