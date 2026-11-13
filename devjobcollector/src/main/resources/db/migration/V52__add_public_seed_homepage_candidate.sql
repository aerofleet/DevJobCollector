ALTER TABLE public_company_seed
    ADD COLUMN homepage_candidate_url VARCHAR(500) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
