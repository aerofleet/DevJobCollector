UPDATE job_posts
SET original_url = CONCAT(REPLACE(original_url, '/w1/recruits/', '/recruitment/'), '/detail'),
    version = version + 1
WHERE source_platform = 'WOOWAHAN'
  AND REGEXP_LIKE(original_url, '^https://career[.]woowahan[.]com/w1/recruits/R[0-9]+$', 'c');
