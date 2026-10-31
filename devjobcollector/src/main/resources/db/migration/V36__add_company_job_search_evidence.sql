ALTER TABLE company_jobs
    ADD COLUMN original_posted_date DATE NULL,
    ADD COLUMN complete_description_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE job_posts
    ADD COLUMN original_posted_date DATE NULL,
    ADD COLUMN complete_description_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
