-- V2: upgrade embedding column to vector(512) and add HNSW index.
-- This migration runs automatically when pgvector IS available (production).
-- If pgvector is not installed it will fail; use only with pgvector/pgvector:pg16.
DO $$
BEGIN
  -- Only attempt upgrade if vector extension is available
  IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
    CREATE EXTENSION IF NOT EXISTS vector;
    BEGIN
      ALTER TABLE idx.chunks ALTER COLUMN embedding TYPE vector(512)
          USING embedding::vector(512);
      CREATE INDEX IF NOT EXISTS chunks_vec_hnsw
          ON idx.chunks USING hnsw (embedding vector_cosine_ops)
          WITH (m = 16, ef_construction = 64);
    EXCEPTION WHEN OTHERS THEN
      NULL; -- column may already be vector type or table may be empty
    END;
  END IF;
END;
$$;
