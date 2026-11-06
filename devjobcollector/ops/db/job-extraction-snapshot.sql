-- Read-only JSONL input for the offline extraction audit. Run in a consistent
-- read-only transaction using the same database timezone as the application.
-- The output contains job text: keep it private and do not publish it.
SET SESSION time_zone = '+09:00';
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;
SELECT JSON_OBJECT('id', id, 'sourcePlatform', source_platform,
                   'applyQual', apply_qual, 'publicPreference', public_preference)
FROM job_posts
WHERE is_active = 1 AND moderation_status = 'ACTIVE' AND end_date >= CURRENT_DATE()
ORDER BY id;
COMMIT;
