CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS rag;
CREATE TABLE rag.documents (
  id UUID PRIMARY KEY,
  owner_type VARCHAR(20) NOT NULL,
  owner_id UUID NOT NULL,
  source VARCHAR(40) NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
  embedding VECTOR(768) NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_rag_doc_chunk UNIQUE (owner_type, owner_id, chunk_index)
);
CREATE INDEX idx_rag_embedding ON rag.documents USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_rag_content_tsv ON rag.documents USING gin (content_tsv);
CREATE INDEX idx_rag_owner ON rag.documents (owner_type, owner_id);
