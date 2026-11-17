-- V9: seed jobs carry no Apply link (issue #13).
-- The V8 migration gave the 18 seeded jobs a source_url, but those URLs
-- are generic portal search pages (LinkedIn / Indeed / Monster / Naukri
-- search for the role) or a company careers homepage (Navy Federal) —
-- never the actual posting. Clicking "Apply" lands the user on a generic
-- search that returns 0 results for the job. The seeded rows have no real
-- posting URL, so they must not offer an Apply link: the web renders the
-- source badge only when sourceUrl is null (JobSourceLink renders
-- nothing). Live feeds (Adzuna) set a real redirect_url per job, so their
-- Apply links are unaffected.
UPDATE job.jobs SET source_url = NULL
WHERE id IN (
  '10000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000002',
  '10000000-0000-4000-8000-000000000003',
  '10000000-0000-4000-8000-000000000004',
  '10000000-0000-4000-8000-000000000005',
  '10000000-0000-4000-8000-000000000006',
  '10000000-0000-4000-8000-000000000007',
  '10000000-0000-4000-8000-000000000008',
  '10000000-0000-4000-8000-000000000009',
  '10000000-0000-4000-8000-000000000010',
  '10000000-0000-4000-8000-000000000011',
  '10000000-0000-4000-8000-000000000012',
  '10000000-0000-4000-8000-000000000013',
  '10000000-0000-4000-8000-000000000014',
  '10000000-0000-4000-8000-000000000015',
  '10000000-0000-4000-8000-000000000016',
  '10000000-0000-4000-8000-000000000017',
  '10000000-0000-4000-8000-000000000018'
);
