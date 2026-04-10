package com.codec.kb.gateway.admin;

import com.codec.kb.gateway.models.ModelTagSupport;
import com.codec.kb.gateway.store.AiModelEntity;
import com.codec.kb.gateway.store.AiModelRepository;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/models")
@Validated
public class AdminModelsController {
  private final AiModelRepository models;

  public AdminModelsController(AiModelRepository models) {
    this.models = models;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Map<String, Object> list(@RequestParam(name = "providerId", required = false) String providerId) {
    List<AiModelEntity> list;
    if (providerId != null && !providerId.isBlank()) {
      UUID pid = parseUuid(providerId);
      list = models.findByProvider_IdOrderByDisplayNameAsc(pid);
    } else {
      list = models.findAll();
      list.sort(Comparator.comparing(AiModelEntity::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase)));
    }

    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (AiModelEntity m : list) {
      if (m == null) continue;
      String providerName = m.getProvider() == null ? "" : (m.getProvider().getName() == null ? "" : m.getProvider().getName());
      List<String> tags = ModelTagSupport.inferTags(m.getModelId(), m.getDisplayName(), providerName);

      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", String.valueOf(m.getId()));
      row.put("providerId", m.getProvider() == null ? "" : String.valueOf(m.getProvider().getId()));
      row.put("providerName", providerName);
      row.put("modelId", m.getModelId() == null ? "" : m.getModelId());
      row.put("displayName", m.getDisplayName() == null ? "" : m.getDisplayName());
      row.put("enabled", m.isEnabled());
      row.put("createdAt", m.getCreatedAt() == null ? "" : m.getCreatedAt().toString());
      row.put("updatedAt", m.getUpdatedAt() == null ? "" : m.getUpdatedAt().toString());
      row.put("tags", tags);
      row.put("capabilities", ModelTagSupport.capabilities(tags));
      out.add(row);
    }
    return Map.of("models", out);
  }

  public record UpdateModelRequest(
      String displayName,
      Boolean enabled
  ) {}

  @PatchMapping("/{id}")
  @Transactional
  public Map<String, Object> update(@PathVariable("id") String id, @RequestBody UpdateModelRequest req) {
    UUID mid = parseUuid(id);
    AiModelEntity m = models.findById(mid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

    if (req != null && req.displayName() != null && !req.displayName().trim().isBlank()) {
      m.setDisplayName(safeDisplayName(req.displayName()));
    }
    if (req != null && req.enabled() != null) {
      m.setEnabled(req.enabled());
    }

    models.save(m);
    return Map.of("ok", true);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public Map<String, Object> delete(@PathVariable("id") String id) {
    UUID mid = parseUuid(id);
    if (!models.existsById(mid)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
    models.deleteById(mid);
    return Map.of("ok", true);
  }

  private static UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
    }
  }

  private static String safeDisplayName(@NotBlank String displayName) {
    String s = displayName == null ? "" : displayName.trim();
    if (s.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
    if (s.length() > 120) s = s.substring(0, 120);
    return s;
  }
}
