package com.codec.kb.index;

import com.codec.kb.common.SearchHit;
import com.codec.kb.common.DocumentDto;
import com.codec.kb.common.ChunkDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HybridIndex {
  private static final class Entry {
    final DocumentDto doc;
    final ChunkDto chunk;
    final float[] vec; // normalized
    final Map<String, Integer> tf;
    final Set<String> tokenSet;
    final int len;

    Entry(DocumentDto doc, ChunkDto chunk, float[] vec, Map<String, Integer> tf, Set<String> tokenSet, int len) {
      this.doc = doc;
      this.chunk = chunk;
      this.vec = vec;
      this.tf = tf;
      this.tokenSet = tokenSet;
      this.len = len;
    }
  }

  private final double bm25K1;
  private final double bm25B;
  private final double wBm25;
  private final double wDense;

  private final ArrayList<Entry> entries = new ArrayList<>();
  private final Map<String, Integer> df = new HashMap<>();
  private double avgLen = 0.0;

  public HybridIndex(double bm25K1, double bm25B, double wBm25, double wDense) {
    this.bm25K1 = bm25K1;
    this.bm25B = bm25B;
    double sum = Math.max(1e-9, wBm25 + wDense);
    this.wBm25 = wBm25 / sum;
    this.wDense = wDense / sum;
  }

  public void rebuild(List<DocumentDto> docs, List<ChunkDto> chunks, Map<String, float[]> embeddings) {
    entries.clear();
    df.clear();
    avgLen = 0.0;

    Map<String, DocumentDto> byId = new HashMap<>();
    for (DocumentDto d : docs) byId.put(d.id(), d);

    long sumLen = 0;
    for (ChunkDto c : chunks) {
      DocumentDto d = byId.get(c.docId());
      if (d == null) continue;

      List<String> tokens = Tokenizer.tokenize(c.text());
      HashMap<String, Integer> tf = new HashMap<>();
      HashSet<String> uniq = new HashSet<>();
      for (String t : tokens) {
        tf.merge(t, 1, Integer::sum);
        uniq.add(t);
      }
      for (String t : uniq) df.merge(t, 1, Integer::sum);

      float[] vec = embeddings.get(c.id());
      entries.add(new Entry(d, c, vec, tf, uniq, tokens.size()));
      sumLen += tokens.size();
    }

    avgLen = entries.isEmpty() ? 0.0 : (sumLen / (double) entries.size());
  }

  public List<SearchHit> search(String kbId, String query, float[] queryVec, int topK) {
    List<String> qTokens = Tokenizer.tokenize(query);
    if (qTokens.isEmpty()) return List.of();

    Map<String, Integer> qtf = new HashMap<>();
    Set<String> quniq = new HashSet<>();
    for (String t : qTokens) {
      qtf.merge(t, 1, Integer::sum);
      quniq.add(t);
    }

    int N = Math.max(1, entries.size());

    double maxBm25 = 0.0;
    double maxDense = 0.0;

    ArrayList<Scored> scored = new ArrayList<>(entries.size());
    for (Entry e : entries) {
      double bm25 = scoreBm25(qtf, quniq, e, N);
      double dense = scoreDense(queryVec, e.vec);
      if (bm25 > maxBm25) maxBm25 = bm25;
      if (dense > maxDense) maxDense = dense;
      scored.add(new Scored(e, bm25, dense));
    }

    double bm25Den = maxBm25 <= 0.0 ? 1.0 : maxBm25;
    double denseDen = maxDense <= 0.0 ? 1.0 : maxDense;

    ArrayList<SearchHit> out = new ArrayList<>();
    for (Scored s : scored) {
      double fused = wBm25 * (s.bm25 / bm25Den) + wDense * (s.dense / denseDen);
      out.add(new SearchHit(s.e.chunk, fused, kbId));
    }

    out.sort(Comparator.comparingDouble(SearchHit::score).reversed());
    if (out.size() > topK) return out.subList(0, topK);
    return out;
  }

  private static final class Scored {
    final Entry e;
    final double bm25;
    final double dense;

    Scored(Entry e, double bm25, double dense) {
      this.e = e;
      this.bm25 = bm25;
      this.dense = dense;
    }
  }

  private double scoreBm25(Map<String, Integer> qtf, Set<String> quniq, Entry e, int N) {
    if (e.len == 0) return 0.0;
    double score = 0.0;
    for (String t : quniq) {
      int f = e.tf.getOrDefault(t, 0);
      if (f == 0) continue;

      int dft = df.getOrDefault(t, 0);
      double idf = Math.log((N - dft + 0.5) / (dft + 0.5) + 1.0);

      double denom = f + bm25K1 * (1.0 - bm25B + bm25B * (e.len / Math.max(1e-9, avgLen)));
      double tfPart = (f * (bm25K1 + 1.0)) / denom;
      score += idf * tfPart;
    }
    return score;
  }

  private static double scoreDense(float[] q, float[] v) {
    if (q == null || v == null) return 0.0;
    int n = Math.min(q.length, v.length);
    if (n == 0) return 0.0;
    double dot = 0.0;
    for (int i = 0; i < n; i++) dot += q[i] * v[i];
    return dot;
  }
}
