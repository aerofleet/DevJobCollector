ALTER TABLE job_posts
    ADD COLUMN public_preference LONGTEXT NULL,
    ADD COLUMN public_preference_conditions LONGTEXT NULL,
    ADD COLUMN public_disqualification LONGTEXT NULL,
    ADD COLUMN public_details_checked_at DATETIME(6) NULL,
    ADD COLUMN public_details_attempted_at DATETIME(6) NULL;

CREATE INDEX idx_job_posts_public_details ON job_posts (source_platform, public_details_checked_at, public_details_attempted_at, id);
