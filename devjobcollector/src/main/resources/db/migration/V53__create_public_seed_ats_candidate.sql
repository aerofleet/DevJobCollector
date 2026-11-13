CREATE TABLE public_seed_ats_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seed_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    source_identifier VARCHAR(150) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DISCOVERED',
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_public_seed_ats (seed_id, provider, source_identifier),
    CONSTRAINT fk_public_seed_ats_seed FOREIGN KEY (seed_id) REFERENCES public_company_seed (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
