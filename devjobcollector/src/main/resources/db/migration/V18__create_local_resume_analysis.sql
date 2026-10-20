-- Local analysis stores derived feedback and job IDs; never raw resume text or external prompts.
CREATE TABLE ai_analysis_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    cache_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recommendation_algo_version VARCHAR(200) NOT NULL,
    consent_version VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    result_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_ai_analysis_cache (user_id, cache_key),
    KEY idx_ai_analysis_user_created (user_id, created_at),
    CONSTRAINT fk_ai_analysis_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_analysis_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_analysis_idempotency (
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    analysis_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, idempotency_key),
    CONSTRAINT fk_ai_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_idempotency_analysis FOREIGN KEY (analysis_id) REFERENCES ai_analysis_request(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_analysis_daily_usage (
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, usage_date),
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_analysis_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    gate_version VARCHAR(50) NOT NULL,
    pii_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    external_call_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_audit_analysis FOREIGN KEY (analysis_id) REFERENCES ai_analysis_request(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_recommendation_feedback (
    analysis_id BIGINT NOT NULL,
    job_post_id BIGINT NOT NULL,
    feedback_type VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (analysis_id, job_post_id),
    CONSTRAINT fk_ai_feedback_analysis FOREIGN KEY (analysis_id) REFERENCES ai_analysis_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_feedback_job FOREIGN KEY (job_post_id) REFERENCES job_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
