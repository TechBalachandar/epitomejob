CREATE SCHEMA IF NOT EXISTS identity;
CREATE TABLE identity.users (
  id UUID PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(60),
  full_name VARCHAR(120) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at TIMESTAMPTZ
);
CREATE INDEX idx_users_email ON identity.users (email);
CREATE TABLE identity.refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES identity.users(id),
  token_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON identity.refresh_tokens (user_id);
CREATE TABLE identity.login_attempts (
  id UUID PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  ip VARCHAR(45),
  success BOOLEAN NOT NULL,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attempts_email_time ON identity.login_attempts (email, attempted_at);
