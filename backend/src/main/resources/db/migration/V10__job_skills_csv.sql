-- Kotlin migration: Job.skills is now stored as private skillsCsv,
-- mapped to column skills_csv. Rename the existing skills column
-- (created in V3) to preserve the seeded job data.
ALTER TABLE job.jobs RENAME COLUMN skills TO skills_csv;
