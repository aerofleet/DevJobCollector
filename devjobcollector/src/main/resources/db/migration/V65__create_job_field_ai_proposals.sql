CREATE TABLE job_field_ai_proposals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_post_id BIGINT NOT NULL,
    job_version BIGINT NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    fields_json JSON NOT NULL,
    review_status VARCHAR(24) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_field_ai_input (job_post_id, input_hash),
    KEY idx_job_field_ai_review (review_status, created_at),
    CONSTRAINT fk_job_field_ai_job FOREIGN KEY (job_post_id) REFERENCES job_posts (id) ON DELETE CASCADE
);
