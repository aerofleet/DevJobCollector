ALTER TABLE public_company_seed
    ADD COLUMN has_it_related_posting BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN relevant_posting_title VARCHAR(500) NULL,
    ADD KEY ix_public_seed_scan_priority (status, has_it_related_posting, homepage_discovery_checked_at, id);
