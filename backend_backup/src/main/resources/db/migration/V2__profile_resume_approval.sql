CREATE SCHEMA IF NOT EXISTS profile;
CREATE TABLE profile.profiles (
  id UUID PRIMARY KEY,
  user_id UUID UNIQUE NOT NULL,
  headline VARCHAR(200),
  summary TEXT,
  location VARCHAR(120),
  work_type VARCHAR(20),
  salary_min INT,
  salary_max INT,
  currency VARCHAR(3) DEFAULT 'USD',
  visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  completeness INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE SCHEMA IF NOT EXISTS resume;
CREATE TABLE resume.resumes (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  blob_path VARCHAR(500) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(10) NOT NULL,
  file_size INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
  parsed_text TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resumes_user ON resume.resumes (user_id);
CREATE SCHEMA IF NOT EXISTS coach;
CREATE TABLE coach.approvals (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  action VARCHAR(50) NOT NULL,
  payload TEXT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  expires_at TIMESTAMPTZ NOT NULL,
  decided_at TIMESTAMPTZ
);
CREATE INDEX idx_approvals_user_status ON coach.approvals (user_id, status);
