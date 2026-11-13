CREATE SCHEMA IF NOT EXISTS notification;
CREATE TABLE notification.notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  link VARCHAR(200) NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notifications_user ON notification.notifications (user_id, created_at DESC);
