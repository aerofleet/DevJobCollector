CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    position VARCHAR(100) NULL,
    tech_stack TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100) NULL AFTER role,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING_EMAIL' AFTER password_hash,
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL' AFTER status,
    ADD COLUMN provider_user_id VARCHAR(255) NULL AFTER provider,
    ADD COLUMN email_verified_at DATETIME(6) NULL AFTER provider_user_id;

CREATE UNIQUE INDEX uk_users_provider_identity ON users (provider, provider_user_id);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_email_verification_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_email_verification_user_active
    ON email_verification_tokens (user_id, used_at, id);
