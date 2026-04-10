package com.codec.kb.common;

import java.util.List;

public record UpsertRequest(
    DocumentDto document,
    List<ChunkDto> chunks
) {}
