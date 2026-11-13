ALTER TABLE public_seed_ats_candidate
    ADD COLUMN target_id BIGINT NULL,
    ADD UNIQUE KEY uk_public_seed_ats_target (target_id),
    ADD CONSTRAINT fk_public_seed_ats_target FOREIGN KEY (target_id) REFERENCES company_source_target (id);
