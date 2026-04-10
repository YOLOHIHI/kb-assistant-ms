package com.codec.kb.common;

public record SearchHit(
    ChunkDto chunk,
    double score,
    String kbId
) {}
