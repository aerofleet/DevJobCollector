CREATE TABLE company_verification_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    evidence_object_key VARCHAR(500) NOT NULL,
    rejection_reason VARCHAR(500) DEFAULT NULL,
    reviewed_by BIGINT DEFAULT NULL,
    requested_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_company_verifications_company_status (company_id, status, requested_at, id),
    KEY idx_company_verifications_status_requested (status, requested_at, id),
    KEY idx_company_verifications_requested_by (requested_by),
    KEY idx_company_verifications_reviewed_by (reviewed_by),
    CONSTRAINT fk_company_verifications_company_id
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_verifications_requested_by
        FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_company_verifications_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
