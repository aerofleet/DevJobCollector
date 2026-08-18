CREATE TABLE company_source_target (
    id BIGINT NOT NULL AUTO_INCREMENT,
    careers_url VARCHAR(1000) DEFAULT NULL,
    collection_tier ENUM('A', 'B', 'C', 'D') NOT NULL,
    company_id BIGINT DEFAULT NULL,
    company_name VARCHAR(150) DEFAULT NULL,
    consecutive_failures INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_checked_at DATETIME(6) DEFAULT NULL,
    last_failure_at DATETIME(6) DEFAULT NULL,
    last_http_status INT DEFAULT NULL,
    last_success_at DATETIME(6) DEFAULT NULL,
    next_collect_at DATETIME(6) DEFAULT NULL,
    provider VARCHAR(30) NOT NULL,
    schema_version VARCHAR(50) DEFAULT NULL,
    source_identifier VARCHAR(150) NOT NULL,
    status ENUM('ACTIVE', 'BLOCKED', 'DEAD', 'DEGRADED', 'DISCOVERED', 'MIGRATED', 'VERIFYING') NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_target_provider_identifier (provider, source_identifier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crawl_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    closure_evaluation_allowed BIT(1) NOT NULL,
    error_type VARCHAR(100) DEFAULT NULL,
    finished_at DATETIME(6) DEFAULT NULL,
    http_status INT DEFAULT NULL,
    item_count INT NOT NULL,
    previous_item_count INT DEFAULT NULL,
    started_at DATETIME(6) NOT NULL,
    status ENUM('EMPTY_SUCCESS', 'FAILED', 'PARTIAL_SUCCESS', 'RATE_LIMITED', 'SCHEMA_CHANGED', 'SUCCESS') NOT NULL,
    target_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crawl_run_target_id (target_id),
    CONSTRAINT fk_crawl_run_target_id
        FOREIGN KEY (target_id) REFERENCES company_source_target (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    apply_qual LONGTEXT DEFAULT NULL,
    company_name VARCHAR(150) NOT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    end_date DATE NOT NULL,
    experience VARCHAR(255) DEFAULT NULL,
    hire_type VARCHAR(255) DEFAULT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    job_category VARCHAR(255) DEFAULT NULL,
    location VARCHAR(255) DEFAULT NULL,
    original_sn VARCHAR(255) NOT NULL,
    original_url TEXT NOT NULL,
    process_info LONGTEXT DEFAULT NULL,
    source_platform ENUM('COMPANY_PAGE', 'GREENHOUSE', 'JOBKOREA', 'LEVER', 'PUBLIC_ALIO', 'SARAMIN') NOT NULL,
    start_date DATE NOT NULL,
    title VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_original (source_platform, original_sn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT DEFAULT NULL,
    job_post_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_job_files_job_post_id (job_post_id),
    CONSTRAINT fk_job_files_job_post_id
        FOREIGN KEY (job_post_id) REFERENCES job_posts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_raw_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fetched_at DATETIME(6) NOT NULL,
    http_status INT DEFAULT NULL,
    provider VARCHAR(30) NOT NULL,
    raw_payload LONGTEXT NOT NULL,
    response_hash VARCHAR(64) NOT NULL,
    source_job_id VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) DEFAULT NULL,
    crawl_run_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_job_raw_snapshot_crawl_run_id (crawl_run_id),
    KEY idx_job_raw_snapshot_target_id (target_id),
    CONSTRAINT fk_job_raw_snapshot_crawl_run_id
        FOREIGN KEY (crawl_run_id) REFERENCES crawl_run (id),
    CONSTRAINT fk_job_raw_snapshot_target_id
        FOREIGN KEY (target_id) REFERENCES company_source_target (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_source_occurrence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    apply_url VARCHAR(1000) DEFAULT NULL,
    content_hash VARCHAR(64) NOT NULL,
    job_posting_id BIGINT DEFAULT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    missed_successful_runs INT NOT NULL,
    is_primary BIT(1) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    published_at DATETIME(6) DEFAULT NULL,
    source_job_id VARCHAR(200) NOT NULL,
    source_location VARCHAR(500) DEFAULT NULL,
    source_status VARCHAR(30) NOT NULL,
    source_title VARCHAR(500) NOT NULL,
    source_url VARCHAR(1000) DEFAULT NULL,
    updated_at_source DATETIME(6) DEFAULT NULL,
    target_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_occurrence_source (provider, target_id, source_job_id),
    KEY idx_job_source_occurrence_target_id (target_id),
    CONSTRAINT fk_job_source_occurrence_target_id
        FOREIGN KEY (target_id) REFERENCES company_source_target (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tech_stacks (
    id INT NOT NULL AUTO_INCREMENT,
    stack_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_stacks_stack_name (stack_name),
    KEY idx_stack_name (stack_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_post_id BIGINT NOT NULL,
    tech_stack_id INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_tech (job_post_id, tech_stack_id),
    KEY idx_post_tags_tech_stack_id (tech_stack_id),
    CONSTRAINT fk_post_tags_job_post_id
        FOREIGN KEY (job_post_id) REFERENCES job_posts (id),
    CONSTRAINT fk_post_tags_tech_stack_id
        FOREIGN KEY (tech_stack_id) REFERENCES tech_stacks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
