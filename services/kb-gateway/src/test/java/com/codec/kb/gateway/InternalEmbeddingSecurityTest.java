package com.codec.kb.gateway;

import com.codec.kb.common.ManagedEmbeddingResponse;
import com.codec.kb.gateway.models.ProviderEmbeddingService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalEmbeddingController.class)
@Import({SecurityConfig.class, InternalEmbeddingSecurityTest.TestConfig.class})
class InternalEmbeddingSecurityTest {
  private static final String INTERNAL_TOKEN = "test-internal-token";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ProviderEmbeddingService embeddings;

  @Test
  void internalEmbeddingsAcceptsInternalTokenWithoutCsrf() throws Exception {
    when(embeddings.embedByModelRef(eq("11111111-1111-1111-1111-111111111111"), anyList()))
        .thenReturn(new ManagedEmbeddingResponse("siliconflow", "BAAI/bge-m3", List.of(List.of(0.1D, 0.2D))));

    mockMvc.perform(post("/internal/embeddings")
            .header("X-Internal-Token", INTERNAL_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "modelRef": "11111111-1111-1111-1111-111111111111",
                  "texts": ["hello"]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.providerName").value("siliconflow"))
        .andExpect(jsonPath("$.modelId").value("BAAI/bge-m3"))
        .andExpect(jsonPath("$.vectors[0][0]").value(0.1D));
  }

  @Test
  void internalEmbeddingsStillRejectsMissingInternalToken() throws Exception {
    mockMvc.perform(post("/internal/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "modelRef": "11111111-1111-1111-1111-111111111111",
                  "texts": ["hello"]
                }
                """))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(embeddings);
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    GatewayConfig gatewayConfig() {
      return new GatewayConfig(INTERNAL_TOKEN, "", "", "", true);
    }
  }
}
