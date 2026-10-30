ALTER TABLE job_posts
    ADD COLUMN supplemental_salary_text LONGTEXT NULL,
    ADD COLUMN supplemental_salary_source_url TEXT NULL,
    ADD COLUMN supplemental_salary_source_hash CHAR(64) NULL,
    ADD COLUMN supplemental_salary_checked_at DATETIME(6) NULL,
    ADD COLUMN supplemental_salary_attempted_at DATETIME(6) NULL;
