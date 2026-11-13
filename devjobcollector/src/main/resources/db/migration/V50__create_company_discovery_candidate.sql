CREATE TABLE company_discovery_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    source_identifier VARCHAR(150) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DISCOVERED',
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_discovery_company_provider_identifier (company_id, provider, source_identifier),
    KEY ix_discovery_status_id (status, id),
    CONSTRAINT fk_discovery_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
