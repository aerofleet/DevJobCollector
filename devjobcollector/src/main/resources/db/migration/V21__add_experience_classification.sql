ALTER TABLE job_posts
    ADD COLUMN experience_category VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN experience_label VARCHAR(255) NOT NULL DEFAULT '경력 확인 필요';

CREATE INDEX idx_job_posts_experience_category ON job_posts (experience_category);
