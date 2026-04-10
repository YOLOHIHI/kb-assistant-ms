package com.codec.kb.ai;

public record ChatMessage(
    String role,
    String content,
    String at,
    String model
) {}
