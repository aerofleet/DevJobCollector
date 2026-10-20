CREATE TABLE ai_evidence_explanation (
    analysis_id BIGINT NOT NULL PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    consent_version VARCHAR(50) NOT NULL,
    gate_version VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    CONSTRAINT fk_ai_explanation_analysis FOREIGN KEY (analysis_id) REFERENCES ai_analysis_request(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Attempt counters survive deletion of an analysis or resume.
CREATE TABLE ai_external_daily_usage (
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, usage_date),
    CONSTRAINT fk_ai_external_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_external_global_usage (
    usage_date DATE NOT NULL PRIMARY KEY,
    attempt_count INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
