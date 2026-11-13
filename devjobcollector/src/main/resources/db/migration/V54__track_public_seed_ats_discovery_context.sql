ALTER TABLE public_seed_ats_candidate
    ADD COLUMN first_homepage_candidate_url VARCHAR(500) NULL,
    ADD COLUMN first_seed_version BIGINT NULL;
