package com.codec.kb.common;

import java.util.List;

public record ManagedEmbeddingResponse(
    String providerName,
    String modelId,
    List<List<Double>> vectors
) {}
