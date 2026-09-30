ALTER TABLE company_verification_requests
    ADD COLUMN reviewed_by_admin BIGINT NULL,
    ADD INDEX idx_company_verifications_reviewed_by_admin (reviewed_by_admin),
    ADD CONSTRAINT fk_company_verifications_reviewed_by_admin
        FOREIGN KEY (reviewed_by_admin) REFERENCES admin_accounts (id) ON DELETE RESTRICT;
