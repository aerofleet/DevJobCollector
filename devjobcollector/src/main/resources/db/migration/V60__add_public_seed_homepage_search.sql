ALTER TABLE public_company_seed
    ADD COLUMN homepage_search_checked_at DATETIME(6) NULL,
    ADD COLUMN homepage_candidate_source VARCHAR(40) NULL,
    ADD COLUMN homepage_candidate_score INT NULL;
