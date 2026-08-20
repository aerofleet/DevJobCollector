DROP PROCEDURE IF EXISTS validate_v3_member_backfill;

DELIMITER //

CREATE PROCEDURE validate_v3_member_backfill()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE provider NOT IN ('LOCAL', 'GOOGLE', 'GITHUB')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_BACKFILL_UNSUPPORTED_PROVIDER';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM users
        WHERE provider IN ('GOOGLE', 'GITHUB')
          AND (provider_user_id IS NULL OR TRIM(provider_user_id) = '')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_BACKFILL_MISSING_SOCIAL_SUBJECT';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT
                provider,
                CASE
                    WHEN provider = 'LOCAL' THEN LOWER(TRIM(email))
                    ELSE provider_user_id
                END COLLATE utf8mb4_bin AS provider_subject
            FROM users
        ) AS legacy_identities
        GROUP BY provider, provider_subject
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_BACKFILL_DUPLICATE_PROVIDER_SUBJECT';
    END IF;
END//

DELIMITER ;

CALL validate_v3_member_backfill();
DROP PROCEDURE validate_v3_member_backfill;

CREATE TABLE personal_profiles (
    user_id BIGINT NOT NULL,
    profile_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_personal_profiles_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_user_consents_user_timeline (user_id, consent_type, policy_version, occurred_at, id),
    CONSTRAINT fk_user_consents_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    issuer VARCHAR(255) DEFAULT NULL,
    provider_email VARCHAR(255) DEFAULT NULL,
    provider_email_verified BOOLEAN DEFAULT NULL,
    last_login_at DATETIME(6) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_identities_provider_subject (provider, provider_subject),
    UNIQUE KEY uk_user_identities_user_provider (user_id, provider),
    CONSTRAINT fk_user_identities_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO user_identities (
    user_id,
    provider,
    provider_subject,
    provider_email,
    provider_email_verified
)
SELECT
    id,
    provider,
    CASE
        WHEN provider = 'LOCAL' THEN LOWER(TRIM(email))
        ELSE provider_user_id
    END,
    email,
    CASE
        WHEN provider = 'LOCAL' THEN email_verified_at IS NOT NULL
        ELSE NULL
    END
FROM users;

INSERT INTO personal_profiles (user_id)
SELECT id
FROM users
WHERE status = 'ACTIVE';
