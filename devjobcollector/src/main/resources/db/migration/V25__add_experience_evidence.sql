ALTER TABLE job_posts
    ADD COLUMN experience_evidence VARCHAR(500) NULL,
    ADD COLUMN experience_method VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';
