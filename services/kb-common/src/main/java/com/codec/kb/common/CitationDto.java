package com.codec.kb.common;

public record CitationDto(
    String docId,
    String filename,
    String chunkId,
    int chunkIndex,
    String sourceHint,
    String snippet,
    String kbId
) {}
