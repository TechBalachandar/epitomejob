-- Resume Builder V1 (RB1): own schema, own table, own history.
-- The monolith's V1–V8 migrations are NOT touched: they run against the
-- backend's datasource/schema; this migration runs only for this service
-- (spring.flyway.schemas=resume_builder), so its flyway_schema_history
-- table also lives in resume_builder.
CREATE SCHEMA IF NOT EXISTS resume_builder;

CREATE TABLE resume_builder.documents (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  title VARCHAR(200) NOT NULL,
  template_id VARCHAR(50) NOT NULL,
  -- The full editable resume state as JSON (see README §State). TEXT, not
  -- JSONB: keeps the migration portable and the JPA mapping trivial; the
  -- service (Jackson) is the only reader/writer.
  state TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_user_id ON resume_builder.documents (user_id);
