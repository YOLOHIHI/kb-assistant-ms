package com.codec.kb.common;

import java.util.List;

public record SearchRequest(
    String query,
    int topK,
    List<String> kbIds
) {}
