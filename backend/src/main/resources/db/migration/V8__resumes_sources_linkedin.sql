-- W6 (contract §2a): one profile, many resumes + job sources (multi-portal).
-- * resume.resumes gains a nullable label (backfilled from the file name).
-- * job.jobs gains source (NOT NULL, default SEED) + source_url (nullable).
-- * profile.profiles gains linkedin_url + work_authorization (both nullable).
-- Demo-data note: the source / source_url values seeded below are DEMO DATA
-- only — each sourceUrl is the portal's REAL search page for that role (or
-- the company's real careers page), never a fabricated job-detail URL. The
-- seeded rows do not link to the actual postings; live feeds arrive in a
-- later slice behind the JobProvider interface.
ALTER TABLE resume.resumes ADD COLUMN label VARCHAR(80);
UPDATE resume.resumes SET label = file_name WHERE label IS NULL;

ALTER TABLE job.jobs ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'SEED';
ALTER TABLE job.jobs ADD COLUMN source_url VARCHAR(500);
CREATE INDEX idx_jobs_source ON job.jobs (source);

ALTER TABLE profile.profiles ADD COLUMN linkedin_url VARCHAR(300);
ALTER TABLE profile.profiles ADD COLUMN work_authorization VARCHAR(20);

-- Seed the 18 existing jobs with a source + sourceUrl, distributed across
-- LINKEDIN / INDEED / MONSTER / NAUKRI / COMPANY. The only company-careers
-- row in the V3 seed is Navy Federal (no Capgemini row exists in V3).
UPDATE job.jobs SET source = 'LINKEDIN', source_url = 'https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer' WHERE id = '10000000-0000-4000-8000-000000000001';
UPDATE job.jobs SET source = 'INDEED', source_url = 'https://www.indeed.com/jobs?q=Staff+Backend+Engineer&l=McLean%2C+VA' WHERE id = '10000000-0000-4000-8000-000000000002';
UPDATE job.jobs SET source = 'MONSTER', source_url = 'https://www.monster.com/jobs/search?q=Java+Backend+Developer&where=Reston%2C+VA' WHERE id = '10000000-0000-4000-8000-000000000003';
UPDATE job.jobs SET source = 'NAUKRI', source_url = 'https://www.naukri.com/principal-software-engineer-jobs' WHERE id = '10000000-0000-4000-8000-000000000004';
UPDATE job.jobs SET source = 'LINKEDIN', source_url = 'https://www.linkedin.com/jobs/search/?keywords=Agentic%20AI%20Engineer' WHERE id = '10000000-0000-4000-8000-000000000005';
UPDATE job.jobs SET source = 'INDEED', source_url = 'https://www.indeed.com/jobs?q=AI+Engineer&l=Washington%2C+DC' WHERE id = '10000000-0000-4000-8000-000000000006';
UPDATE job.jobs SET source = 'MONSTER', source_url = 'https://www.monster.com/jobs/search?q=Generative+AI+Engineer&where=Reston%2C+VA' WHERE id = '10000000-0000-4000-8000-000000000007';
UPDATE job.jobs SET source = 'NAUKRI', source_url = 'https://www.naukri.com/senior-ai-platform-engineer-jobs' WHERE id = '10000000-0000-4000-8000-000000000008';
UPDATE job.jobs SET source = 'COMPANY', source_url = 'https://www.navyfederal.org/careers' WHERE id = '10000000-0000-4000-8000-000000000009';
UPDATE job.jobs SET source = 'LINKEDIN', source_url = 'https://www.linkedin.com/jobs/search/?keywords=Android%20Engineer' WHERE id = '10000000-0000-4000-8000-000000000010';
UPDATE job.jobs SET source = 'INDEED', source_url = 'https://www.indeed.com/jobs?q=Senior+Platform+Engineer&l=McLean%2C+VA' WHERE id = '10000000-0000-4000-8000-000000000011';
UPDATE job.jobs SET source = 'MONSTER', source_url = 'https://www.monster.com/jobs/search?q=Cloud+Platform+Engineer&where=Washington%2C+DC' WHERE id = '10000000-0000-4000-8000-000000000012';
UPDATE job.jobs SET source = 'NAUKRI', source_url = 'https://www.naukri.com/devops-platform-engineer-jobs' WHERE id = '10000000-0000-4000-8000-000000000013';
UPDATE job.jobs SET source = 'LINKEDIN', source_url = 'https://www.linkedin.com/jobs/search/?keywords=Machine%20Learning%20Engineer' WHERE id = '10000000-0000-4000-8000-000000000014';
UPDATE job.jobs SET source = 'INDEED', source_url = 'https://www.indeed.com/jobs?q=Machine+Learning+Engineer&l=Washington%2C+DC' WHERE id = '10000000-0000-4000-8000-000000000015';
UPDATE job.jobs SET source = 'MONSTER', source_url = 'https://www.monster.com/jobs/search?q=Machine+Learning+Engineer&where=Reston%2C+VA' WHERE id = '10000000-0000-4000-8000-000000000016';
UPDATE job.jobs SET source = 'NAUKRI', source_url = 'https://www.naukri.com/senior-software-engineer-jobs' WHERE id = '10000000-0000-4000-8000-000000000017';
UPDATE job.jobs SET source = 'LINKEDIN', source_url = 'https://www.linkedin.com/jobs/search/?keywords=Full%20Stack%20Engineer' WHERE id = '10000000-0000-4000-8000-000000000018';
