-- The source explicitly requires at least five years in product design or equivalent.
UPDATE job_posts
SET experience_category = 'EXPERIENCED',
    experience_label = '경력 5년 이상',
    experience_evidence = '• Minimum 5 years in Product Design (or equivalent).',
    experience_method = 'RULE',
    version = version + 1
WHERE id = 12280
  AND source_platform = 'GREENHOUSE'
  AND original_url = 'https://www.pinterestcareers.com/jobs/?gh_jid=8249279'
  AND apply_qual LIKE '%Minimum 5 years in Product Design (or equivalent)%'
  AND reviewed_source_hash IS NULL;
