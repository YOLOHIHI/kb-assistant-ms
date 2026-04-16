package com.codec.kb.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PgvectorStatusService {
  private static final Logger log = LoggerFactory.getLogger(PgvectorStatusService.class);
  private static final long CACHE_TTL_MS = 30_000L;

  public record Status(boolean pgvectorInstalled, String embeddingColumnType) {
    public boolean nativeVectorColumn() {
      String type = embeddingColumnType == null ? "" : embeddingColumnType.trim().toLowerCase();
      return "vector".equals(type) || "halfvec".equals(type);
    }

    public boolean denseRetrievalAvailable() {
      return pgvectorInstalled && nativeVectorColumn();
    }
  }

  private final JdbcTemplate jdbc;

  private volatile Status cached = new Status(false, "unknown");
  private volatile long cachedAt = 0L;

  public PgvectorStatusService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Status currentStatus() {
    long now = System.currentTimeMillis();
    if (cachedAt != 0L && now - cachedAt < CACHE_TTL_MS) {
      return cached;
    }
    return refresh();
  }

  public synchronized Status refresh() {
    try {
      Boolean installed = jdbc.queryForObject(
          "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
          Boolean.class
      );
      String columnType = jdbc.queryForObject(
          """
              SELECT COALESCE(udt_name, data_type)
              FROM information_schema.columns
              WHERE table_schema = 'idx' AND table_name = 'chunks' AND column_name = 'embedding'
              """,
          String.class
      );
      Status status = new Status(Boolean.TRUE.equals(installed), normalize(columnType));
      cached = status;
      cachedAt = System.currentTimeMillis();
      return status;
    } catch (Exception e) {
      log.warn("Failed to inspect pgvector status: {}", e.getMessage());
      Status status = new Status(false, "unknown");
      cached = status;
      cachedAt = System.currentTimeMillis();
      return status;
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logStatusOnStartup() {
    Status status = refresh();
    log.info(
        "Index pgvector status: installed={}, embeddingColumnType={}, denseRetrievalAvailable={}",
        status.pgvectorInstalled(),
        status.embeddingColumnType(),
        status.denseRetrievalAvailable()
    );
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return "unknown";
    return value.trim();
  }
}
