CREATE TABLE public_company_seed (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(50) NOT NULL,
    business_number_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DISCOVERED',
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_public_seed_source_business (source, business_number_hash),
    KEY ix_public_seed_status_id (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
