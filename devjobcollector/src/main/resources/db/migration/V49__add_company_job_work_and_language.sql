ALTER TABLE company_jobs
    ADD COLUMN work_arrangement VARCHAR(40) NULL,
    ADD COLUMN korean_level VARCHAR(40) NULL,
    ADD COLUMN english_level VARCHAR(40) NULL,
    ADD COLUMN recruiter_note VARCHAR(2000) NULL,
    ADD COLUMN referral_source VARCHAR(40) NULL;
