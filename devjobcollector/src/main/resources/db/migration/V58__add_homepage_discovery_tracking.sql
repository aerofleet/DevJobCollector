ALTER TABLE public_company_seed
    ADD COLUMN homepage_discovery_checked_at DATETIME(6) NULL,
    ADD KEY ix_public_seed_homepage_discovery (status, homepage_discovery_checked_at, id);

ALTER TABLE companies
    ADD COLUMN homepage_discovery_checked_at DATETIME(6) NULL,
    ADD KEY ix_company_homepage_discovery (status, homepage_discovery_checked_at, id);

ALTER TABLE public_seed_ats_candidate
    ADD COLUMN discovery_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL_HTML';

ALTER TABLE company_discovery_candidate
    ADD COLUMN homepage_url VARCHAR(500) NULL,
    ADD COLUMN discovery_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL_HTML';
