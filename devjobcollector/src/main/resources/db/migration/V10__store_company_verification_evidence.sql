CREATE TABLE company_verification_evidence (
    request_id BIGINT NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (request_id),
    CONSTRAINT fk_company_verification_evidence_request
        FOREIGN KEY (request_id) REFERENCES company_verification_requests (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
