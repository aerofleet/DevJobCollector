CREATE TABLE IF NOT EXISTS company_source_target (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NULL,
    provider VARCHAR(30) NOT NULL,
    source_identifier VARCHAR(150) NOT NULL,
    careers_url VARCHAR(1000) NULL,
    status VARCHAR(30) NOT NULL,
    collection_tier VARCHAR(10) NOT NULL,
    last_success_at TIMESTAMP(6) NULL,
    last_failure_at TIMESTAMP(6) NULL,
    last_checked_at TIMESTAMP(6) NULL,
    consecutive_failures INT NOT NULL DEFAULT 0,
    last_http_status INT NULL,
    schema_version VARCHAR(50) NULL,
    next_collect_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_target_provider_identifier UNIQUE (provider, source_identifier),
    INDEX idx_target_collect_due (status, next_collect_at)
);

CREATE TABLE IF NOT EXISTS crawl_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_id BIGINT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    status VARCHAR(30) NOT NULL,
    http_status INT NULL,
    item_count INT NOT NULL DEFAULT 0,
    previous_item_count INT NULL,
    error_type VARCHAR(100) NULL,
    closure_evaluation_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_crawl_run_target FOREIGN KEY (target_id) REFERENCES company_source_target(id),
    INDEX idx_crawl_run_target_started (target_id, started_at)
);

CREATE TABLE IF NOT EXISTS job_raw_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    crawl_run_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    source_job_id VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) NULL,
    http_status INT NULL,
    response_hash CHAR(64) NOT NULL,
    raw_payload LONGTEXT NOT NULL,
    fetched_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_snapshot_run FOREIGN KEY (crawl_run_id) REFERENCES crawl_run(id),
    CONSTRAINT fk_snapshot_target FOREIGN KEY (target_id) REFERENCES company_source_target(id),
    INDEX idx_snapshot_source (provider, target_id, source_job_id),
    INDEX idx_snapshot_retention (fetched_at)
);

CREATE TABLE IF NOT EXISTS job_source_occurrence (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_posting_id BIGINT NULL,
    target_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    source_job_id VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) NULL,
    apply_url VARCHAR(1000) NULL,
    source_title VARCHAR(500) NOT NULL,
    source_location VARCHAR(500) NULL,
    source_status VARCHAR(30) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    updated_at_source TIMESTAMP(6) NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    missed_successful_runs INT NOT NULL DEFAULT 0,
    content_hash CHAR(64) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_occurrence_target FOREIGN KEY (target_id) REFERENCES company_source_target(id),
    CONSTRAINT uk_occurrence_source UNIQUE (provider, target_id, source_job_id),
    INDEX idx_occurrence_seen (target_id, last_seen_at),
    INDEX idx_occurrence_status (source_status, last_seen_at)
);
