package com.codec.kb.index;

import java.util.List;

/**
 * Strategy for producing embedding vectors for the KB "local" embedding mode.
 *
 * <p>Two implementations are available, selected by {@code kb.embedder.mode}:
 * <ul>
 *   <li>{@link DjlLocalEmbeddingBackend} ({@code djl}) runs the model in-JVM via DJL + ONNX Runtime.
 *   <li>{@link HttpLocalEmbeddingBackend} ({@code http}) calls the external Python {@code kb-embedder} service.
 * </ul>
 *
 * <p>The "api" and "managed" embedding paths are handled separately inside
 * {@link EmbeddingFacade} and are unaffected by this strategy.
 */
public interface LocalEmbeddingBackend {
  List<float[]> embed(List<String> texts);
}
