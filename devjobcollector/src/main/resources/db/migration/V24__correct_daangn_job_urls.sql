UPDATE job_posts
SET original_url = CONCAT('https://careers.daangn.com/jobs/role/', SUBSTRING_INDEX(original_sn, ':', -1), '/'),
    version = version + 1
WHERE source_platform = 'GREENHOUSE'
  AND original_sn REGEXP '^daangn:[0-9]+$'
  AND original_url IN (
      CONCAT('https://about.daangn.com?gh_jid=', SUBSTRING_INDEX(original_sn, ':', -1)),
      CONCAT('https://about.daangn.com/?gh_jid=', SUBSTRING_INDEX(original_sn, ':', -1))
  );
