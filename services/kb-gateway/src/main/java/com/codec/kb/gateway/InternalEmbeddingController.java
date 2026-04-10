package com.codec.kb.gateway;

import com.codec.kb.common.ManagedEmbeddingRequest;
import com.codec.kb.common.ManagedEmbeddingResponse;
import com.codec.kb.gateway.models.ProviderEmbeddingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalEmbeddingController {
  private static final String INTERNAL_HEADER = "X-Internal-Token";

  private final GatewayConfig cfg;
  private final ProviderEmbeddingService embeddings;

  public InternalEmbeddingController(GatewayConfig cfg, ProviderEmbeddingService embeddings) {
    this.cfg = cfg;
    this.embeddings = embeddings;
  }

  @PostMapping(path = "/internal/embeddings", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ManagedEmbeddingResponse embed(
      @RequestHeader(name = INTERNAL_HEADER, required = false) String token,
      @RequestBody ManagedEmbeddingRequest req
  ) {
    if (token == null || !token.equals(cfg.internalToken())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return embeddings.embedByModelRef(req == null ? null : req.modelRef(), req == null ? null : req.texts());
  }
}
