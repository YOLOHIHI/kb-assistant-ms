-- idx schema: knowledge bases, documents, chunks
-- pgvector extension is optional; embedding stored as TEXT for local-dev compatibility.
-- In production (pgvector/pgvector:pg16 image), run V2__vector_upgrade.sql to
-- convert the embedding column to vector(512) and add the HNSW index.
DO $$
BEGIN
  CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION WHEN OTHERS THEN
  NULL; -- pgvector not available; embedding column stays TEXT
END;
$$;

CREATE SCHEMA IF NOT EXISTS idx;

CREATE TABLE idx.knowledge_bases (
    id                 VARCHAR(64)  PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    embedding_mode     VARCHAR(16)  NOT NULL DEFAULT 'local',
    embedding_model    VARCHAR(255),
    embedding_base_url VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE idx.documents (
    id           VARCHAR(64)  PRIMARY KEY,
    kb_id        VARCHAR(64)  NOT NULL REFERENCES idx.knowledge_bases(id) ON DELETE CASCADE,
    filename     VARCHAR(512) NOT NULL,
    content_type VARCHAR(128),
    size_bytes   BIGINT       NOT NULL DEFAULT 0,
    sha256       VARCHAR(64),
    category     VARCHAR(255),
    tags         TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX docs_kb_idx ON idx.documents (kb_id);

CREATE TABLE idx.chunks (
    id          VARCHAR(64)   PRIMARY KEY,
    doc_id      VARCHAR(64)   NOT NULL REFERENCES idx.documents(id) ON DELETE CASCADE,
    kb_id       VARCHAR(64)   NOT NULL,
    chunk_index INTEGER       NOT NULL,
    content     TEXT          NOT NULL,
    source_hint VARCHAR(1024),
    -- stored as TEXT ("[0.1,0.2,...]"); upgraded to vector(512) in V2 when pgvector is available
    embedding   TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX chunks_kb_idx  ON idx.chunks (kb_id);
CREATE INDEX chunks_doc_idx ON idx.chunks (doc_id);

-- HNSW index requires vector type; created in V2__vector_upgrade.sql when pgvector is available
