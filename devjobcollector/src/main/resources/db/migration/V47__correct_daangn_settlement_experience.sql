-- Greenhouse's Prior Experience is 경력; the one-year requirement is preferred only.
UPDATE job_posts
SET experience = '경력',
    experience_category = 'EXPERIENCED',
    experience_label = '경력',
    experience_evidence = '경력',
    experience_method = 'SOURCE',
    hire_type = '계약직',
    version = version + 1
WHERE id = 12017
  AND source_platform = 'GREENHOUSE'
  AND company_name = '당근'
  AND original_url = 'https://careers.daangn.com/jobs/role/7995021003/'
  AND reviewed_source_hash IS NULL;
