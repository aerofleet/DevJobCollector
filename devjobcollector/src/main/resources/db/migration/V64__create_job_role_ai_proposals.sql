CREATE TABLE job_role_ai_proposals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_post_id BIGINT NOT NULL,
    job_version BIGINT NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    catalog_version VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    result_mode VARCHAR(16) NOT NULL,
    suggested_role VARCHAR(32) NOT NULL,
    source_evidence VARCHAR(300) NOT NULL,
    review_status VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_role_ai_input (job_post_id, input_hash),
    KEY idx_job_role_ai_review (review_status, created_at),
    CONSTRAINT fk_job_role_ai_job FOREIGN KEY (job_post_id) REFERENCES job_posts (id) ON DELETE CASCADE
);

CREATE TABLE job_role_ai_daily_budget (
    usage_day DATE NOT NULL,
    reserved_requests INT NOT NULL DEFAULT 0,
    reserved_micro_usd BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (usage_day)
);

CREATE TABLE job_role_ai_claims (
    job_post_id BIGINT NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    usage_day DATE NOT NULL,
    reserved_micro_usd BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_post_id, input_hash),
    CONSTRAINT fk_job_role_ai_claim_job FOREIGN KEY (job_post_id) REFERENCES job_posts (id) ON DELETE CASCADE
);
