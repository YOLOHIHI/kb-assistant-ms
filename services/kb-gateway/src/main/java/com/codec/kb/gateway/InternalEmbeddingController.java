package com.codec.kb.gateway;

import com.codec.kb.common.ManagedEmbeddingRequest;
import com.codec.kb.common.ManagedEmbeddingResponse;
import com.codec.kb.gateway.models.ProviderEmbeddingService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalEmbeddingController {
  private final ProviderEmbeddingService embeddings;

  public InternalEmbeddingController(ProviderEmbeddingService embeddings) {
    this.embeddings = embeddings;
  }

  @PostMapping(path = "/internal/embeddings", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ManagedEmbeddingResponse embed(@RequestBody ManagedEmbeddingRequest req) {
    return embeddings.embedByModelRef(req == null ? null : req.modelRef(), req == null ? null : req.texts());
  }
}
