package com.codec.kb.common;

import java.util.List;
import java.util.Map;

/**
 * contextSize: number of previous messages to include as LLM conversation history.
 *   0  = no history (default)
 *   N  = include last N messages (each message = one user or assistant turn)
 */
public record ChatRequest(
    String sessionId,
    String message,
    int topK,
    List<String> kbIds,
    Map<String, Integer> kbTopK,
    String model,
    LlmConfig llm,
    Boolean appendUser,
    int contextSize
) {}
