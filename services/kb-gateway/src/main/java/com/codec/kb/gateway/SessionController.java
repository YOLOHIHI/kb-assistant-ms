package com.codec.kb.gateway;

import com.codec.kb.gateway.auth.AuthUtil;
import com.codec.kb.gateway.clients.DownstreamClientSupport;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@RestController
public class SessionController {
  private static final String INTERNAL_HEADER = "X-Internal-Token";
  private static final String USER_HEADER = "X-User-Id";

  private final GatewayConfig cfg;
  private final RestClient rc;

  public SessionController(GatewayConfig cfg, RestClient rc) {
    this.cfg = cfg;
    this.rc = rc;
  }

  @GetMapping("/api/sessions")
  public Map<String, Object> listSessions() {
    String userId = AuthUtil.requirePrincipal().id().toString();
    try {
      Map<?, ?> r = rc.get()
          .uri(cfg.aiUrl() + "/internal/sessions")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .header(USER_HEADER, userId)
          .retrieve()
          .body(Map.class);
      return (Map<String, Object>) r;
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "会话服务错误");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "会话服务不可用");
    }
  }

  @PostMapping(path = "/api/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> createSession(@RequestBody Map<String, Object> body) {
    String userId = AuthUtil.requirePrincipal().id().toString();
    try {
      Map<?, ?> r = rc.post()
          .uri(cfg.aiUrl() + "/internal/sessions")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .header(USER_HEADER, userId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
      return (Map<String, Object>) r;
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "会话服务错误");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "会话服务不可用");
    }
  }

  @GetMapping("/api/sessions/{id}")
  public Map<String, Object> getSession(@PathVariable("id") String id) {
    String userId = AuthUtil.requirePrincipal().id().toString();
    try {
      Map<?, ?> r = rc.get()
          .uri(cfg.aiUrl() + "/internal/sessions/" + id)
          .header(INTERNAL_HEADER, cfg.internalToken())
          .header(USER_HEADER, userId)
          .retrieve()
          .body(Map.class);
      return (Map<String, Object>) r;
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "会话服务错误");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "会话服务不可用");
    }
  }

  @PatchMapping(path = "/api/sessions/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Object renameSession(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    String userId = AuthUtil.requirePrincipal().id().toString();
    try {
      return rc.method(HttpMethod.PATCH)
          .uri(cfg.aiUrl() + "/internal/sessions/" + id)
          .header(INTERNAL_HEADER, cfg.internalToken())
          .header(USER_HEADER, userId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Object.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "会话服务错误");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "会话服务不可用");
    }
  }

  @DeleteMapping("/api/sessions/{id}")
  public Map<String, Object> deleteSession(@PathVariable("id") String id) {
    String userId = AuthUtil.requirePrincipal().id().toString();
    try {
      Map<?, ?> r = rc.delete()
          .uri(cfg.aiUrl() + "/internal/sessions/" + id)
          .header(INTERNAL_HEADER, cfg.internalToken())
          .header(USER_HEADER, userId)
          .retrieve()
          .body(Map.class);
      return (Map<String, Object>) r;
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "会话服务错误");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "会话服务不可用");
    }
  }
}
