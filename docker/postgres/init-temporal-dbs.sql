-- EpitomeJob W4 — Temporal databases (contract §2a).
-- Mounted to /docker-entrypoint-initdb.d/ in the root compose file.
-- Runs ONLY when the postgres data volume is created fresh. The existing
-- backend_pgdata volume already exists, so on the user's Mac these two
-- databases must be created once by hand first (see the header comment
-- in docker-compose.yml); a second CREATE DATABASE errors
-- "already exists", which is harmless.
CREATE DATABASE temporal;
CREATE DATABASE temporal_visibility;
