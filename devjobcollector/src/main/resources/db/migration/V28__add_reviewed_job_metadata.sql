ALTER TABLE job_posts
    ADD COLUMN reviewed_experience_category VARCHAR(20) NULL,
    ADD COLUMN reviewed_experience_label VARCHAR(255) NULL,
    ADD COLUMN reviewed_experience_evidence VARCHAR(1000) NULL,
    ADD COLUMN reviewed_location VARCHAR(255) NULL,
    ADD COLUMN reviewed_source_hash VARCHAR(64) NULL,
    ADD COLUMN effective_location VARCHAR(255) NULL;

UPDATE job_posts SET effective_location = location;

ALTER TABLE job_metadata_ai_review
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at DATETIME(6) NULL,
    ADD COLUMN applied_experience_category VARCHAR(20) NULL,
    ADD COLUMN applied_experience_label VARCHAR(255) NULL,
    ADD COLUMN applied_location VARCHAR(255) NULL;
