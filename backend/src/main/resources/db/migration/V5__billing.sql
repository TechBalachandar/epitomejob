-- Billing (M14, W4 contract §1b). Valid PostgreSQL; H2 compatibility NOT needed
-- (test profile has Flyway disabled; Testcontainers ITs run real Postgres).
CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.plans (
  code VARCHAR(40) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  price_cents INT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'USD',
  duration_days INT NULL,
  description TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Seed rows (exact codes/prices from the contract — do not invent others).
INSERT INTO billing.plans (code, name, price_cents, currency, duration_days, description, active) VALUES
('SPRINT_PASS', 'Sprint Pass', 7900, 'USD', 30, '30 days of full access — one-time payment.', TRUE),
('LIFETIME', 'Lifetime', 14900, 'USD', NULL, 'Lifetime access — one-time payment.', TRUE),
('HUMAN_REVIEWED', 'Human-Reviewed Package', 29900, 'USD', NULL, 'Human-reviewed resume + plan. Fulfilled manually by our team — we contact you after checkout.', TRUE);

CREATE TABLE billing.checkout_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  plan_code VARCHAR(40) NOT NULL,
  provider VARCHAR(20) NOT NULL,
  provider_session_id VARCHAR(255) NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_billing_sessions_provider ON billing.checkout_sessions (provider_session_id);
CREATE INDEX idx_billing_sessions_user ON billing.checkout_sessions (user_id);

CREATE TABLE billing.entitlements (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  plan_code VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NULL
);
-- One ACTIVE entitlement per (user_id, plan_code).
CREATE UNIQUE INDEX uq_billing_active_entitlement ON billing.entitlements (user_id, plan_code) WHERE status = 'ACTIVE';
CREATE INDEX idx_billing_entitlements_user ON billing.entitlements (user_id);
