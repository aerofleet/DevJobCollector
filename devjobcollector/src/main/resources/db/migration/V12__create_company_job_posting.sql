-- Append the new value without reordering existing ENUM ordinals.
ALTER TABLE job_posts MODIFY COLUMN source_platform
    ENUM('COMPANY_PAGE', 'GREENHOUSE', 'JOBKOREA', 'LEVER', 'PUBLIC_ALIO', 'SARAMIN', 'COMPANY_DIRECT') NOT NULL;

CREATE TABLE company_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    published_job_id BIGINT NULL,
    title VARCHAR(255), job_category VARCHAR(255), experience VARCHAR(255),
    location VARCHAR(255), hire_type VARCHAR(255), tech_stacks VARCHAR(1000),
    description LONGTEXT, end_date DATE, apply_url VARCHAR(2000),
    state VARCHAR(30) NOT NULL, review_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0, updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_company_job_published (published_job_id),
    KEY idx_company_job_owner (company_id, id),
    KEY idx_company_job_review (state, updated_at),
    CONSTRAINT fk_company_job_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_company_job_published FOREIGN KEY (published_job_id) REFERENCES job_posts(id)
);
CREATE TABLE company_job_audits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_job_id BIGINT NOT NULL, actor_id BIGINT NULL, actor_type VARCHAR(20) NOT NULL,
    action VARCHAR(40) NOT NULL, reason VARCHAR(500), created_at DATETIME(6) NOT NULL,
    KEY idx_company_job_audit (company_job_id, id),
    CONSTRAINT fk_company_job_audit FOREIGN KEY (company_job_id) REFERENCES company_jobs(id)
);
CREATE TABLE company_job_events (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    job_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL,
    KEY idx_company_job_event (job_id, kind),
    CONSTRAINT fk_company_job_event FOREIGN KEY (job_id) REFERENCES job_posts(id)
);
