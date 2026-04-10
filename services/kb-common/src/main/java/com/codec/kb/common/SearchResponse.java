package com.codec.kb.common;

import java.util.List;

public record SearchResponse(List<SearchHit> hits) {}
