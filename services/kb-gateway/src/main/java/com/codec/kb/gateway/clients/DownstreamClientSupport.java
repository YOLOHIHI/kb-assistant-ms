package com.codec.kb.gateway.clients;

import com.codec.kb.common.util.WebUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

public final class DownstreamClientSupport {
  private static final ObjectMapper OM = new ObjectMapper();

  private DownstreamClientSupport() {}

  public static ResponseStatusException translate(RestClientResponseException e, String action) {
    HttpStatus upstream = HttpStatus.resolve(e.getStatusCode().value());
    String detail = extractDetail(WebUtils.safeTrim(e.getResponseBodyAsString()));
    String reason = detail.isBlank() ? action : action + "：" + detail;
    if (upstream == HttpStatus.UNAUTHORIZED || upstream == HttpStatus.FORBIDDEN) {
      return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason, e);
    }
    if (upstream != null && upstream.is4xxClientError()) {
      return new ResponseStatusException(upstream, reason, e);
    }
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason, e);
  }

  public static ResponseStatusException translate(RestClientException e, String action) {
    String detail = WebUtils.safeTrim(e.getMessage());
    String reason = detail.isBlank() ? action : action + "：" + detail;
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason, e);
  }

  public static ResponseStatusException translateRaw(
      HttpStatusCode statusCode, String body, String action) {
    HttpStatus upstream = HttpStatus.resolve(statusCode.value());
    String detail = extractDetail(WebUtils.safeTrim(body));
    String reason = detail.isBlank() ? action : action + "：" + detail;
    if (upstream == HttpStatus.UNAUTHORIZED || upstream == HttpStatus.FORBIDDEN) {
      return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
    }
    if (upstream != null && upstream.is4xxClientError()) {
      return new ResponseStatusException(upstream, reason);
    }
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
  }

  private static String extractDetail(String body) {
    if (body == null || body.isBlank()) return "";
    try {
      JsonNode root = OM.readTree(body);
      for (String field : new String[]{"message", "error", "reason"}) {
        JsonNode value = root.path(field);
        if (value.isTextual()) {
          String text = WebUtils.safeTrim(value.asText());
          if (!text.isBlank()) return text;
        }
      }
    } catch (Exception ignored) {}
    return truncate(body, 240);
  }

  private static String truncate(String value, int max) {
    if (value == null) return "";
    if (value.length() <= max) return value;
    return value.substring(0, max) + "...";
  }
}
