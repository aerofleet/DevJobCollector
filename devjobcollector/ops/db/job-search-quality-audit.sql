-- Read-only aggregate audit for job-search coverage.
-- Run against the DJC database; output contains no job text or identifiers.
WITH active_jobs AS (
    SELECT
        id,
        source_platform,
        apply_qual,
        process_info,
        hire_type
    FROM job_posts
    WHERE is_active = 1
      AND end_date >= CURRENT_DATE
), coverage AS (
    SELECT
        CAST(source_platform AS CHAR) AS source_platform,
        COUNT(*) AS active_jobs,
        COALESCE(SUM(apply_qual IS NOT NULL AND TRIM(apply_qual) <> ''), 0) AS body_jobs,
        COALESCE(SUM(process_info IS NOT NULL AND TRIM(process_info) <> ''), 0) AS process_jobs,
        COALESCE(SUM(hire_type IS NOT NULL AND TRIM(hire_type) <> ''), 0) AS hire_type_jobs,
        COALESCE(SUM(EXISTS (
            SELECT 1
            FROM post_tags pt
            WHERE pt.job_post_id = active_jobs.id
        )), 0) AS tagged_jobs,
        ROUND(AVG(CASE
            WHEN apply_qual IS NOT NULL AND TRIM(apply_qual) <> ''
            THEN CHAR_LENGTH(apply_qual)
            ELSE NULL
        END), 0) AS avg_body_length
    FROM active_jobs
    GROUP BY source_platform
), totals AS (
    SELECT
        'ALL' AS source_platform,
        COUNT(*) AS active_jobs,
        COALESCE(SUM(apply_qual IS NOT NULL AND TRIM(apply_qual) <> ''), 0) AS body_jobs,
        COALESCE(SUM(process_info IS NOT NULL AND TRIM(process_info) <> ''), 0) AS process_jobs,
        COALESCE(SUM(hire_type IS NOT NULL AND TRIM(hire_type) <> ''), 0) AS hire_type_jobs,
        COALESCE(SUM(EXISTS (
            SELECT 1
            FROM post_tags pt
            WHERE pt.job_post_id = active_jobs.id
        )), 0) AS tagged_jobs,
        ROUND(AVG(CASE
            WHEN apply_qual IS NOT NULL AND TRIM(apply_qual) <> ''
            THEN CHAR_LENGTH(apply_qual)
            ELSE NULL
        END), 0) AS avg_body_length
    FROM active_jobs
)
SELECT
    source_platform,
    active_jobs,
    body_jobs,
    ROUND(body_jobs * 100.0 / NULLIF(active_jobs, 0), 1) AS body_coverage_pct,
    process_jobs,
    ROUND(process_jobs * 100.0 / NULLIF(active_jobs, 0), 1) AS process_coverage_pct,
    hire_type_jobs,
    ROUND(hire_type_jobs * 100.0 / NULLIF(active_jobs, 0), 1) AS hire_type_coverage_pct,
    tagged_jobs,
    ROUND(tagged_jobs * 100.0 / NULLIF(active_jobs, 0), 1) AS tech_tag_coverage_pct,
    avg_body_length
FROM (
    SELECT * FROM totals
    UNION ALL
    SELECT * FROM coverage
) audit
ORDER BY source_platform <> 'ALL', source_platform;
