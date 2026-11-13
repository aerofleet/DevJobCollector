ALTER TABLE public_seed_ats_candidate
    ADD COLUMN board_verified_at DATETIME(6) NULL,
    ADD COLUMN board_job_count INT NULL,
    ADD COLUMN board_schema_version VARCHAR(100) NULL,
    ADD COLUMN board_seed_version BIGINT NULL;
