package com.codec.kb.index;

import ai.djl.ModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DjlLocalEmbeddingBackend implements LocalEmbeddingBackend {
  private static final Logger log = LoggerFactory.getLogger(DjlLocalEmbeddingBackend.class);

  private final EmbedderConfig cfg;
  private ZooModel<String, float[]> model;
  private Path modelDir;

  public DjlLocalEmbeddingBackend(EmbedderConfig cfg) {
    this.cfg = cfg;
  }

  @PostConstruct
  public void init() throws ModelException, IOException {
    String modelRef = cfg.modelOrDefault();
    Path cacheRoot = resolveCacheRoot();
    this.modelDir = cacheRoot.resolve(sanitizeSegment(modelRef));

    log.info("[djl-embedder] Preparing model {} (pooling={}, normalize={}, maxLength={}). Cache dir: {}",
        modelRef, cfg.poolingOrDefault(), cfg.normalizeOrDefault(), cfg.maxLengthOrDefault(), modelDir);

    if (!modelRef.startsWith("djl://") && !modelRef.startsWith("http://")
        && !modelRef.startsWith("https://") && !modelRef.startsWith("file:")) {
      ensureHuggingFaceModel(modelRef, modelDir);
    }

    Criteria.Builder<String, float[]> builder = Criteria.builder()
        .setTypes(String.class, float[].class)
        .optEngine("OnnxRuntime")
        .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
        .optArgument("pooling", cfg.poolingOrDefault())
        .optArgument("normalize", String.valueOf(cfg.normalizeOrDefault()))
        .optArgument("maxLength", String.valueOf(cfg.maxLengthOrDefault()))
        .optArgument("padding", "true")
        .optArgument("truncation", "true");

    if (modelRef.startsWith("djl://") || modelRef.startsWith("http://")
        || modelRef.startsWith("https://") || modelRef.startsWith("file:")) {
      builder.optModelUrls(modelRef);
    } else {
      builder.optModelPath(modelDir);
    }

    long t0 = System.nanoTime();
    try {
      this.model = builder.build().loadModel();
    } catch (UnsatisfiedLinkError e) {
      throw new IllegalStateException(
          "Failed to load ONNX Runtime native library. On Windows this usually means the "
              + "Microsoft Visual C++ 2019/2022 x64 Redistributable is missing. Install it from "
              + "https://aka.ms/vs/17/release/vc_redist.x64.exe and restart the IDE / JVM. "
              + "On Linux/macOS, ensure the OS is not too old for the bundled onnxruntime "
              + "native binaries. Alternatively set KB_EMBEDDER_MODE=http to use the external "
              + "kb-embedder service instead. Original error: " + e.getMessage(),
          e);
    }
    long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
    log.info("[djl-embedder] Model loaded in {} ms. Running warmup...", elapsedMs);

    try (Predictor<String, float[]> p = model.newPredictor()) {
      float[] warmup = p.predict("warmup");
      log.info("[djl-embedder] Warmup complete. Vector dim = {}.", warmup.length);
    } catch (TranslateException e) {
      throw new IllegalStateException("DJL embedder warmup failed", e);
    }
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    if (texts == null || texts.isEmpty()) return List.of();
    if (model == null) throw new IllegalStateException("DJL embedder not initialized");

    List<String> normalized = new ArrayList<>(texts.size());
    for (String t : texts) normalized.add(t == null ? "" : t);

    try (Predictor<String, float[]> predictor = model.newPredictor()) {
      List<float[]> out = new ArrayList<>(normalized.size());
      for (String t : normalized) {
        out.add(predictor.predict(t));
      }
      return out;
    } catch (TranslateException e) {
      throw new RuntimeException("DJL embedding failed: " + e.getMessage(), e);
    }
  }

  @PreDestroy
  public void close() {
    if (model != null) {
      model.close();
      model = null;
    }
  }

  private Path resolveCacheRoot() {
    String override = cfg.cacheDir();
    if (override != null && !override.isBlank()) return Path.of(override.trim());
    String djlCache = System.getenv("DJL_CACHE_DIR");
    if (djlCache != null && !djlCache.isBlank()) return Path.of(djlCache).resolve("kb-embedder");
    return Path.of(System.getProperty("user.home"), ".djl.ai", "kb-embedder");
  }

  /**
   * Downloads a HuggingFace text-embedding model (config + tokenizer + ONNX weights) into
   * {@code dir} the first time it is needed. Subsequent runs skip the download.
   *
   * <p>Supports repos where the ONNX file lives either at the root or under the {@code onnx/}
   * subfolder (Xenova / onnx-community layouts). The target directory always ends up with
   * {@code config.json}, {@code tokenizer.json}, and {@code model.onnx} at its top level,
   * which is what DJL's {@code OnnxRuntime} engine + {@code TextEmbeddingTranslatorFactory}
   * expect.
   */
  private void ensureHuggingFaceModel(String repoId, Path dir) throws IOException {
    Files.createDirectories(dir);
    Path marker = dir.resolve(".ready");
    if (Files.exists(marker)) return;

    String endpoint = hfEndpoint();
    String base = endpoint + "/" + repoId.replaceFirst("^/+", "") + "/resolve/main/";

    log.info("[djl-embedder] Downloading model files from {} ...", base);
    try (HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()) {

      Map<String, Path> required = new LinkedHashMap<>();
      required.put("config.json", dir.resolve("config.json"));
      required.put("tokenizer.json", dir.resolve("tokenizer.json"));

      List<String> optionalAccessories = List.of(
          "tokenizer_config.json",
          "special_tokens_map.json",
          "vocab.txt",
          "sentence_bert_config.json");

      for (Map.Entry<String, Path> e : required.entrySet()) {
        downloadTo(http, base + e.getKey(), e.getValue(), true);
      }
      for (String f : optionalAccessories) {
        downloadTo(http, base + f, dir.resolve(f), false);
      }

      Path onnxTarget = dir.resolve("model.onnx");
      boolean onnxOk = downloadTo(http, base + "onnx/model.onnx", onnxTarget, false);
      if (!onnxOk) {
        onnxOk = downloadTo(http, base + "model.onnx", onnxTarget, false);
      }
      if (!onnxOk) {
        throw new IOException("Failed to download ONNX weights for " + repoId
            + " (tried onnx/model.onnx and model.onnx under " + base + ")");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while downloading model", e);
    }

    Files.writeString(marker, "ok");
    log.info("[djl-embedder] Model files ready at {}", dir);
  }

  private String hfEndpoint() {
    String env = System.getenv("HF_ENDPOINT");
    if (env != null && !env.isBlank()) return env.trim().replaceAll("/+$", "");
    return "https://huggingface.co";
  }

  private boolean downloadTo(HttpClient http, String url, Path target, boolean required)
      throws IOException, InterruptedException {
    if (Files.exists(target) && Files.size(target) > 0) return true;
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofMinutes(10))
        .GET()
        .build();
    Path tmp = target.resolveSibling(target.getFileName() + ".part");
    HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
    if (resp.statusCode() / 100 != 2) {
      try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
      if (required) {
        throw new IOException("HTTP " + resp.statusCode() + " downloading " + url);
      }
      return false;
    }
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    log.info("[djl-embedder]   fetched {} ({} bytes)", target.getFileName(), Files.size(target));
    return true;
  }

  private static String sanitizeSegment(String s) {
    return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
  }
}
