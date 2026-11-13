ALTER TABLE public_seed_ats_candidate
    ADD COLUMN review_note VARCHAR(500) NULL,
    ADD COLUMN review_reference_url VARCHAR(1000) NULL;
