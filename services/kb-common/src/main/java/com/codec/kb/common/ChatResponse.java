package com.codec.kb.common;

import java.util.List;

public record ChatResponse(
    String sessionId,
    String answer,
    String answerHash,
    List<CitationDto> citations
) {}
