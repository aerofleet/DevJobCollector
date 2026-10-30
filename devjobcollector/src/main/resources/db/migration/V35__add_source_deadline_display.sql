ALTER TABLE job_posts ADD COLUMN source_deadline_display VARCHAR(32) NULL;

-- The Kakao source was verified as endDate=null and hidePostingPeriodFlag=false for this posting.
UPDATE job_posts SET source_deadline_display='RECRUITMENT_END', version=version+1
WHERE id=9439 AND source_platform='KAKAO' AND original_sn='kakao-careers:S-4432'
  AND original_url='https://careers.kakao.com/jobs/S-4432' AND end_date='9999-12-31'
  AND source_deadline_display IS NULL;
