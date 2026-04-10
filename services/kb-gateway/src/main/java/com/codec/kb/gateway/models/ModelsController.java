package com.codec.kb.gateway.models;

import com.codec.kb.gateway.store.AiModelEntity;
import com.codec.kb.gateway.store.AiModelRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelsController {
  private final AiModelRepository models;

  public ModelsController(AiModelRepository models) {
    this.models = models;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Map<String, Object> listEnabled() {
    List<AiModelEntity> list = models.findByEnabledTrueAndProvider_EnabledTrueOrderByDisplayNameAsc();
    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (AiModelEntity m : list) {
      if (m == null) continue;
      String providerName = m.getProvider() == null ? "" : (m.getProvider().getName() == null ? "" : m.getProvider().getName());
      List<String> tags = ModelTagSupport.inferTags(m.getModelId(), m.getDisplayName(), providerName);

      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", String.valueOf(m.getId()));
      row.put("modelId", m.getModelId() == null ? "" : m.getModelId());
      row.put("displayName", m.getDisplayName() == null ? "" : m.getDisplayName());
      row.put("providerName", providerName);
      row.put("tags", tags);
      row.put("capabilities", ModelTagSupport.capabilities(tags));
      out.add(row);
    }
    return Map.of("models", out);
  }
}
