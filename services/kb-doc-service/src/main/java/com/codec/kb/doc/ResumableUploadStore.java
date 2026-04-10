package com.codec.kb.doc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResumableUploadStore {
  private static final ObjectMapper OM = new ObjectMapper();
  // uploadId -> base dir
  private final ConcurrentHashMap<String, Path> uploads = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> uploadLocks = new ConcurrentHashMap<>();

  public static final class LegacyUploadStateException extends IOException {
    public LegacyUploadStateException() {
      super("legacy resumable upload state cannot be resumed; restart upload");
    }
  }

  public record CoveredRange(long start, long end) {}

  public record ApplyChunkResult(UploadMeta meta, boolean completedNow) {}

  public record UploadMeta(
      String uploadId,
      String kbId,
      String filename,
      long totalSize,
      String contentType,
      long received,
      String status,  // "IN_PROGRESS" | "COMPLETE"
      List<CoveredRange> coveredRanges
  ) {}

  public Path initUpload(String dataDir, String kbId, String uploadId, String filename,
      long totalSize, String contentType) throws IOException {
    Path base = Path.of(dataDir).toAbsolutePath().normalize()
        .resolve("resumable").resolve(kbId).resolve(uploadId);
    Files.createDirectories(base);
    uploads.put(uploadId, base);
    uploadLocks.putIfAbsent(base.toString(), new Object());

    UploadMeta meta = new UploadMeta(uploadId, kbId, filename, totalSize, contentType, 0, "IN_PROGRESS", List.of());
    Files.writeString(base.resolve("meta.json"), OM.writeValueAsString(meta));

    // pre-create the data file
    Path dataFile = base.resolve("data.bin");
    if (totalSize > 0) {
      try (RandomAccessFile raf = new RandomAccessFile(dataFile.toFile(), "rw")) {
        raf.setLength(totalSize);
      }
    }
    return base;
  }

  public void writeChunk(String dataDir, String kbId, String uploadId, long start, byte[] data) throws IOException {
    applyChunk(dataDir, kbId, uploadId, start, data);
  }

  public ApplyChunkResult applyChunk(String dataDir, String kbId, String uploadId, long start, byte[] data) throws IOException {
    Path base = resolveBase(dataDir, kbId, uploadId);
    synchronized (resolveLock(base)) {
      UploadMeta old = readValidatedMeta(base);
      long end = validateChunkBounds(old.totalSize(), start, data);
      ArrayList<CoveredRange> coveredRanges = readCoveredRanges(old);
      boolean wasComplete = "COMPLETE".equals(old.status());

      if (isDuplicate(coveredRanges, start, end)) {
        UploadMeta meta = toMeta(old, coveredRanges);
        writeMeta(base, meta);
        return new ApplyChunkResult(meta, false);
      }
      rejectPartialOverlap(coveredRanges, start, end);

      Path dataFile = base.resolve("data.bin");
      try (RandomAccessFile raf = new RandomAccessFile(dataFile.toFile(), "rw")) {
        raf.seek(start);
        raf.write(data);
      }

      ArrayList<CoveredRange> updatedRanges = mergeRange(coveredRanges, new CoveredRange(start, end));
      UploadMeta meta = toMeta(old, updatedRanges);
      writeMeta(base, meta);
      return new ApplyChunkResult(meta, !wasComplete && "COMPLETE".equals(meta.status()));
    }
  }

  public UploadMeta getMeta(String dataDir, String kbId, String uploadId) throws IOException {
    Path base = resolveBase(dataDir, kbId, uploadId);
    synchronized (resolveLock(base)) {
      return readValidatedMeta(base);
    }
  }

  public Path getDataFile(String dataDir, String kbId, String uploadId) throws IOException {
    Path base = resolveBase(dataDir, kbId, uploadId);
    return base.resolve("data.bin");
  }

  private Path resolveBase(String dataDir, String kbId, String uploadId) throws IOException {
    Path base = Path.of(dataDir).toAbsolutePath().normalize()
        .resolve("resumable").resolve(kbId).resolve(uploadId);
    if (!Files.exists(base)) {
      throw new IOException("upload not found: " + uploadId);
    }
    return base;
  }

  private UploadMeta readMeta(Path base) throws IOException {
    String json = Files.readString(base.resolve("meta.json"));
    return OM.readValue(json, UploadMeta.class);
  }

  private UploadMeta readValidatedMeta(Path base) throws IOException {
    return normalizeMeta(readMeta(base));
  }

  private void writeMeta(Path base, UploadMeta meta) throws IOException {
    Files.writeString(base.resolve("meta.json"), OM.writeValueAsString(meta));
  }

  private UploadMeta toMeta(UploadMeta old, List<CoveredRange> coveredRanges) {
    long received = countCoveredBytes(coveredRanges);
    String status = received >= old.totalSize() ? "COMPLETE" : "IN_PROGRESS";
    return new UploadMeta(old.uploadId(), old.kbId(), old.filename(),
        old.totalSize(), old.contentType(), received, status, List.copyOf(coveredRanges));
  }

  private UploadMeta normalizeMeta(UploadMeta meta) throws IOException {
    return toMeta(meta, readCoveredRanges(meta));
  }

  private ArrayList<CoveredRange> readCoveredRanges(UploadMeta meta) throws IOException {
    ArrayList<CoveredRange> coveredRanges = new ArrayList<>();
    List<CoveredRange> existing = meta.coveredRanges();
    if (existing == null) {
      if (meta.received() == 0) return coveredRanges;
      throw new LegacyUploadStateException();
    }
    if (existing.isEmpty()) {
      if (meta.received() == 0) return coveredRanges;
      throw new LegacyUploadStateException();
    }

    coveredRanges.addAll(existing);
    coveredRanges.sort(Comparator.comparingLong(CoveredRange::start));
    ArrayList<CoveredRange> merged = mergeAdjacentRanges(coveredRanges);
    long coveredBytes = countCoveredBytes(merged);
    String expectedStatus = coveredBytes >= meta.totalSize() ? "COMPLETE" : "IN_PROGRESS";
    if (coveredBytes != meta.received() || !expectedStatus.equals(meta.status())) {
      throw new LegacyUploadStateException();
    }
    return merged;
  }

  private Object resolveLock(Path base) {
    return uploadLocks.computeIfAbsent(base.toString(), ignored -> new Object());
  }

  private long validateChunkBounds(long totalSize, long start, byte[] data) {
    if (data == null || data.length == 0) {
      throw new IllegalArgumentException("chunk data required");
    }
    if (start < 0) {
      throw new IllegalArgumentException("chunk start out of range");
    }
    long end;
    try {
      end = Math.addExact(start, data.length - 1L);
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("chunk range overflow", ex);
    }
    if (end >= totalSize) {
      throw new IllegalArgumentException("chunk end out of range");
    }
    return end;
  }

  private boolean isDuplicate(List<CoveredRange> coveredRanges, long start, long end) {
    for (CoveredRange coveredRange : coveredRanges) {
      if (start >= coveredRange.start() && end <= coveredRange.end()) {
        return true;
      }
    }
    return false;
  }

  private void rejectPartialOverlap(List<CoveredRange> coveredRanges, long start, long end) {
    for (CoveredRange coveredRange : coveredRanges) {
      boolean overlaps = start <= coveredRange.end() && end >= coveredRange.start();
      if (overlaps) {
        throw new IllegalArgumentException("chunk overlaps existing coverage");
      }
    }
  }

  private ArrayList<CoveredRange> mergeRange(List<CoveredRange> coveredRanges, CoveredRange newRange) {
    ArrayList<CoveredRange> merged = new ArrayList<>(coveredRanges);
    merged.add(newRange);
    merged.sort(Comparator.comparingLong(CoveredRange::start));
    return mergeAdjacentRanges(merged);
  }

  private ArrayList<CoveredRange> mergeAdjacentRanges(List<CoveredRange> ranges) {
    ArrayList<CoveredRange> merged = new ArrayList<>();
    for (CoveredRange range : ranges) {
      if (merged.isEmpty()) {
        merged.add(range);
        continue;
      }
      CoveredRange last = merged.get(merged.size() - 1);
      if (range.start() <= last.end() + 1) {
        merged.set(merged.size() - 1, new CoveredRange(last.start(), Math.max(last.end(), range.end())));
        continue;
      }
      merged.add(range);
    }
    return merged;
  }

  private long countCoveredBytes(List<CoveredRange> coveredRanges) {
    long total = 0;
    for (CoveredRange coveredRange : coveredRanges) {
      total += coveredRange.end() - coveredRange.start() + 1;
    }
    return total;
  }
}
