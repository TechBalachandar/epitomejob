CREATE SCHEMA IF NOT EXISTS release_control;

CREATE TABLE release_control.release_candidates (
  id UUID PRIMARY KEY,
  repository VARCHAR(255) NOT NULL,
  pull_request_number BIGINT NOT NULL CHECK (pull_request_number > 0),
  source_sha VARCHAR(64) NOT NULL CHECK (source_sha ~ '^[0-9a-fA-F]{40,64}$'),
  state VARCHAR(32) NOT NULL,
  created_by UUID NOT NULL REFERENCES identity.users(id),
  idempotency_key VARCHAR(128) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_release_idempotency UNIQUE (created_by, idempotency_key)
);

CREATE INDEX idx_release_candidates_created_by
  ON release_control.release_candidates (created_by, created_at DESC);
CREATE INDEX idx_release_candidates_repository_pr
  ON release_control.release_candidates (repository, pull_request_number);

CREATE TABLE release_control.audit_events (
  id UUID PRIMARY KEY,
  release_id UUID NOT NULL REFERENCES release_control.release_candidates(id),
  actor_id UUID,
  actor_type VARCHAR(24) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  from_state VARCHAR(32),
  to_state VARCHAR(32),
  source_sha VARCHAR(64) NOT NULL,
  artifact_digest VARCHAR(255),
  correlation_id VARCHAR(128),
  reason VARCHAR(1000),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_release_audit_timeline
  ON release_control.audit_events (release_id, occurred_at, id);

-- Audit is append-only. Application roles must not be granted UPDATE/DELETE
-- on this table in production; this trigger also rejects accidental mutation.
CREATE OR REPLACE FUNCTION release_control.reject_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'release audit events are append-only';
END;
$$;

CREATE TRIGGER trg_release_audit_immutable
  BEFORE UPDATE OR DELETE ON release_control.audit_events
  FOR EACH ROW EXECUTE FUNCTION release_control.reject_audit_mutation();
