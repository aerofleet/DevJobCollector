CREATE TABLE job_metadata_ai_review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_post_id BIGINT NOT NULL,
    job_version BIGINT NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    source_key VARCHAR(255) NOT NULL,
    model VARCHAR(80) NOT NULL,
    experience_suggestion VARCHAR(255) NOT NULL,
    experience_quote VARCHAR(1000) NOT NULL,
    location_suggestion VARCHAR(255) NOT NULL,
    location_quote VARCHAR(1000) NOT NULL,
    review_status VARCHAR(30) NOT NULL,
    requested_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_job_metadata_ai_job (job_post_id, created_at),
    CONSTRAINT fk_job_metadata_ai_job FOREIGN KEY (job_post_id) REFERENCES job_posts(id)
);
