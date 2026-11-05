-- Instacart/Greenhouse job 8120705; do not overwrite reviewed experience.
UPDATE job_posts
SET hire_type = 'Regular · Full time',
    experience_category = 'EXPERIENCED',
    experience_label = '경력 12~15년',
    experience_evidence = '12-15 years of progressive professional experience, with at least 5 years in a leadership role overseeing IT risk-based audits at a high-growth, global company.',
    experience_method = 'RULE',
    version = version + 1
WHERE id = 11951
  AND source_platform = 'GREENHOUSE'
  AND company_name = 'Instacart'
  AND original_url LIKE '%gh_jid=8120705%'
  AND reviewed_source_hash IS NULL;
