ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE companies
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE job_posts
    ADD COLUMN moderation_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD INDEX idx_job_posts_moderation_status (moderation_status);

CREATE TABLE admin_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    mfa_secret_ciphertext VARBINARY(512) NULL,
    mfa_key_id VARCHAR(100) NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    credential_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_accounts_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_account_permissions (
    admin_id BIGINT NOT NULL,
    permission VARCHAR(50) NOT NULL,
    granted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (admin_id, permission),
    CONSTRAINT fk_admin_permissions_account
        FOREIGN KEY (admin_id) REFERENCES admin_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_sessions (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admin_id BIGINT NOT NULL,
    refresh_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    csrf_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    rotated_from_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_sessions_refresh_hash UNIQUE (refresh_hash),
    CONSTRAINT fk_admin_sessions_account
        FOREIGN KEY (admin_id) REFERENCES admin_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_sessions_rotated_from
        FOREIGN KEY (rotated_from_id) REFERENCES admin_sessions (id) ON DELETE SET NULL,
    INDEX idx_admin_sessions_admin_expiry (admin_id, expires_at),
    INDEX idx_admin_sessions_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actor_admin_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NULL,
    reason VARCHAR(500) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    result VARCHAR(30) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_audit_actor_time (actor_admin_id, occurred_at),
    INDEX idx_admin_audit_target_time (target_type, target_id, occurred_at),
    INDEX idx_admin_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_idempotency_keys (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    response_status INT NULL,
    response_body JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_idempotency_key UNIQUE (admin_id, idempotency_key),
    CONSTRAINT fk_admin_idempotency_account
        FOREIGN KEY (admin_id) REFERENCES admin_accounts (id) ON DELETE CASCADE,
    INDEX idx_admin_idempotency_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_moderation_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    old_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    admin_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_user_moderation_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_moderation_admin FOREIGN KEY (admin_id) REFERENCES admin_accounts (id),
    INDEX idx_user_moderation_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE company_verification_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    old_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    admin_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_company_verification_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_company_verification_admin FOREIGN KEY (admin_id) REFERENCES admin_accounts (id),
    INDEX idx_company_verification_company_time (company_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_moderation_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    old_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    admin_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_job_moderation_job FOREIGN KEY (job_id) REFERENCES job_posts (id),
    CONSTRAINT fk_job_moderation_admin FOREIGN KEY (admin_id) REFERENCES admin_accounts (id),
    INDEX idx_job_moderation_job_time (job_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
