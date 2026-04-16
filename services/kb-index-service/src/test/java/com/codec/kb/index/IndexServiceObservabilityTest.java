package com.codec.kb.index;

import com.codec.kb.common.ChunkDto;
import com.codec.kb.common.DocumentDto;
import com.codec.kb.common.UpsertRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexServiceObservabilityTest {
  @Mock
  private KbJpaRepository kbRepo;
  @Mock
  private DocumentJpaRepository docRepo;
  @Mock
  private ChunkJpaRepository chunkRepo;
  @Mock
  private EmbeddingFacade embeddingFacade;
  @Mock
  private PgvectorStatusService pgvectorStatus;

  private IndexService service;

  @BeforeEach
  void setUp() {
    when(kbRepo.existsById(IndexService.DEFAULT_KB_ID)).thenReturn(true);
    service = new IndexService(
        kbRepo,
        docRepo,
        chunkRepo,
        embeddingFacade,
        new IndexTuning(new IndexTuning.Bm25(1.2, 0.75), new IndexTuning.Hybrid(0.35, 0.65)),
        pgvectorStatus
    );
  }

  @Test
  void statsReportBm25OnlyWhenEmbeddingColumnIsStillText() {
    KbEntity kb = kb("kb_demo", "demo", "api", "model-uuid");
    when(kbRepo.findById("kb_demo")).thenReturn(Optional.of(kb));
    when(docRepo.countByKbId("kb_demo")).thenReturn(3L);
    when(docRepo.sumSizeBytesByKbId("kb_demo")).thenReturn(4096L);
    when(chunkRepo.countByKbId("kb_demo")).thenReturn(12L);
    when(chunkRepo.countByKbIdAndEmbeddingIsNotNull("kb_demo")).thenReturn(9L);
    when(pgvectorStatus.currentStatus()).thenReturn(new PgvectorStatusService.Status(true, "text"));

    Map<String, Object> stats = service.stats("kb_demo");

    assertThat(stats)
        .containsEntry("kbId", "kb_demo")
        .containsEntry("documents", 3L)
        .containsEntry("chunks", 12L)
        .containsEntry("chunksWithEmbedding", 9L)
        .containsEntry("chunksWithoutEmbedding", 3L)
        .containsEntry("pgvectorInstalled", true)
        .containsEntry("embeddingColumnType", "text")
        .containsEntry("denseRetrievalAvailable", false)
        .containsEntry("retrievalMode", "bm25-only")
        .containsEntry("embeddingMode", "api")
        .containsEntry("embeddingModel", "model-uuid");
  }

  @Test
  void statsReportHybridWhenEmbeddingColumnIsNativeVector() {
    KbEntity kb = kb("kb_demo", "demo", "api", "model-uuid");
    when(kbRepo.findById("kb_demo")).thenReturn(Optional.of(kb));
    when(docRepo.countByKbId("kb_demo")).thenReturn(3L);
    when(docRepo.sumSizeBytesByKbId("kb_demo")).thenReturn(4096L);
    when(chunkRepo.countByKbId("kb_demo")).thenReturn(12L);
    when(chunkRepo.countByKbIdAndEmbeddingIsNotNull("kb_demo")).thenReturn(9L);
    when(pgvectorStatus.currentStatus()).thenReturn(new PgvectorStatusService.Status(true, "vector"));

    Map<String, Object> stats = service.stats("kb_demo");

    assertThat(stats)
        .containsEntry("embeddingColumnType", "vector")
        .containsEntry("denseRetrievalAvailable", true)
        .containsEntry("retrievalMode", "hybrid");
  }

  @Test
  void reindexReportsHowManyEmbeddingsWereWritten() {
    ChunkEntity chunkA = chunk("chk_a", "kb_demo", "a");
    ChunkEntity chunkB = chunk("chk_b", "kb_demo", "b");
    when(chunkRepo.findByKbId("kb_demo")).thenReturn(List.of(chunkA, chunkB));
    when(embeddingFacade.embed(eq("kb_demo"), eq(List.of("a", "b"))))
        .thenReturn(List.of(new float[] {0.1f}, new float[] {0.2f}));
    when(pgvectorStatus.currentStatus()).thenReturn(new PgvectorStatusService.Status(true, "vector"));

    Map<String, Object> result = service.reindexAllEmbeddings("kb_demo");

    assertThat(result)
        .containsEntry("ok", true)
        .containsEntry("kbId", "kb_demo")
        .containsEntry("chunks", 2L)
        .containsEntry("chunksWithEmbedding", 2L)
        .containsEntry("chunksWithoutEmbedding", 0L)
        .containsEntry("pgvectorInstalled", true)
        .containsEntry("embeddingColumnType", "vector");
    assertThat(chunkA.getEmbedding()).isNotNull();
    assertThat(chunkB.getEmbedding()).isNotNull();
    verify(chunkRepo).saveAll(any());
  }

  @Test
  void upsertFailsBeforePersistingWhenEmbeddingUnavailable() {
    when(embeddingFacade.embed(eq("kb_demo"), eq(List.of("alpha", "beta"))))
        .thenThrow(new IllegalStateException("embedding offline"));

    assertThatThrownBy(() -> service.upsert("kb_demo", upsert("doc_demo", "alpha", "beta")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("embedding offline");

    verify(chunkRepo, never()).deleteByDocId(any());
    verify(docRepo, never()).deleteById(any());
    verify(docRepo, never()).save(any());
    verify(chunkRepo, never()).saveAll(any());
  }

  @Test
  void reindexFailsWithoutWritingPartialEmbeddingsWhenEmbeddingUnavailable() {
    ChunkEntity chunkA = chunk("chk_a", "kb_demo", "a");
    ChunkEntity chunkB = chunk("chk_b", "kb_demo", "b");
    when(chunkRepo.findByKbId("kb_demo")).thenReturn(List.of(chunkA, chunkB));
    when(embeddingFacade.embed(eq("kb_demo"), eq(List.of("a", "b"))))
        .thenThrow(new IllegalStateException("embedding offline"));

    assertThatThrownBy(() -> service.reindexAllEmbeddings("kb_demo"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("embedding offline");

    assertThat(chunkA.getEmbedding()).isNull();
    assertThat(chunkB.getEmbedding()).isNull();
    verify(chunkRepo, never()).saveAll(any());
    verifyNoInteractions(docRepo);
  }

  private static UpsertRequest upsert(String docId, String... texts) {
    DocumentDto document = new DocumentDto(
        docId,
        docId + ".txt",
        "text/plain",
        128L,
        "sha256",
        Instant.now().toString(),
        Instant.now().toString(),
        null,
        null
    );
    List<ChunkDto> chunks = new java.util.ArrayList<>();
    for (int i = 0; i < texts.length; i++) {
      chunks.add(new ChunkDto("chk_" + i, docId, i, texts[i], "doc.txt#chunk=" + i));
    }
    return new UpsertRequest(document, chunks);
  }

  private static KbEntity kb(String id, String name, String mode, String model) {
    KbEntity kb = new KbEntity();
    kb.setId(id);
    kb.setName(name);
    kb.setEmbeddingMode(mode);
    kb.setEmbeddingModel(model);
    kb.setCreatedAt(Instant.now());
    kb.setUpdatedAt(Instant.now());
    return kb;
  }

  private static ChunkEntity chunk(String id, String kbId, String content) {
    ChunkEntity chunk = new ChunkEntity();
    chunk.setId(id);
    chunk.setKbId(kbId);
    chunk.setDocId("doc_" + id);
    chunk.setChunkIndex(0);
    chunk.setContent(content);
    chunk.setCreatedAt(Instant.now());
    return chunk;
  }
}
