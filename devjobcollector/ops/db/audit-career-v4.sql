-- DJC Career Hub V4 audit (read-only, aggregate output only)
-- Privacy: never emit row-level user, job, resume, application, or memo data.

SELECT 'resume_total' AS metric, COUNT(*) AS value FROM resumes
UNION ALL
SELECT 'bookmark_total', COUNT(*) FROM job_bookmarks
UNION ALL
SELECT 'view_history_total', COUNT(*) FROM job_view_history
UNION ALL
SELECT 'application_total', COUNT(*) FROM applications
UNION ALL
SELECT 'resume_user_orphan_count', COUNT(*)
FROM resumes career_record
LEFT JOIN users owner ON owner.id = career_record.user_id
WHERE owner.id IS NULL
UNION ALL
SELECT 'bookmark_user_orphan_count', COUNT(*)
FROM job_bookmarks career_record
LEFT JOIN users owner ON owner.id = career_record.user_id
WHERE owner.id IS NULL
UNION ALL
SELECT 'view_history_user_orphan_count', COUNT(*)
FROM job_view_history career_record
LEFT JOIN users owner ON owner.id = career_record.user_id
WHERE owner.id IS NULL
UNION ALL
SELECT 'application_user_orphan_count', COUNT(*)
FROM applications career_record
LEFT JOIN users owner ON owner.id = career_record.user_id
WHERE owner.id IS NULL
UNION ALL
SELECT 'bookmark_job_orphan_count', COUNT(*)
FROM job_bookmarks career_record
LEFT JOIN job_posts job_post ON job_post.id = career_record.job_post_id
WHERE job_post.id IS NULL
UNION ALL
SELECT 'view_history_job_orphan_count', COUNT(*)
FROM job_view_history career_record
LEFT JOIN job_posts job_post ON job_post.id = career_record.job_post_id
WHERE job_post.id IS NULL
UNION ALL
SELECT 'application_job_orphan_count', COUNT(*)
FROM applications career_record
LEFT JOIN job_posts job_post ON job_post.id = career_record.job_post_id
WHERE job_post.id IS NULL
UNION ALL
SELECT 'bookmark_owner_job_duplicate_count', COALESCE(SUM(duplicate_count - 1), 0)
FROM (
    SELECT COUNT(*) AS duplicate_count
    FROM job_bookmarks
    GROUP BY user_id, job_post_id
    HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'view_history_owner_job_duplicate_count', COALESCE(SUM(duplicate_count - 1), 0)
FROM (
    SELECT COUNT(*) AS duplicate_count
    FROM job_view_history
    GROUP BY user_id, job_post_id
    HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'application_owner_job_duplicate_count', COALESCE(SUM(duplicate_count - 1), 0)
FROM (
    SELECT COUNT(*) AS duplicate_count
    FROM applications
    GROUP BY user_id, job_post_id
    HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'resume_invalid_status_count', COUNT(*)
FROM resumes
WHERE resume_status NOT IN ('DRAFT', 'READY', 'ARCHIVED')
UNION ALL
SELECT 'resume_blank_title_count', COUNT(*)
FROM resumes
WHERE TRIM(title) = ''
UNION ALL
SELECT 'view_history_invalid_count_count', COUNT(*)
FROM job_view_history
WHERE view_count <= 0
UNION ALL
SELECT 'view_history_invalid_chronology_count', COUNT(*)
FROM job_view_history
WHERE last_viewed_at < first_viewed_at
UNION ALL
SELECT 'application_invalid_status_count', COUNT(*)
FROM applications
WHERE application_status NOT IN (
    'APPLIED',
    'DOCUMENT_SCREENING',
    'INTERVIEW',
    'OFFERED',
    'REJECTED',
    'WITHDRAWN'
);
