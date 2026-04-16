-- V3: correct the dense-storage model.
-- The system supports multiple embedding models, and some managed models emit
-- 4096-dimensional vectors. A fixed vector(512) column cannot represent them.
-- Use pgvector's native variable-dimension vector type instead.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
    RAISE EXCEPTION 'pgvector extension is required for native vector storage. Use the pgvector/pgvector:pg16 image (or install the vector extension into the existing Postgres instance) before running this migration.';
  END IF;

  CREATE EXTENSION IF NOT EXISTS vector;

  DROP INDEX IF EXISTS idx.chunks_vec_hnsw;

  ALTER TABLE idx.chunks
      ALTER COLUMN embedding TYPE vector
      USING CASE
          WHEN embedding IS NULL THEN NULL
          ELSE embedding::vector
      END;
END;
$$;
