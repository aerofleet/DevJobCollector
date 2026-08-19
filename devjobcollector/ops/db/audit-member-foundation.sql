-- DJC member/auth foundation audit (read-only, aggregate output only)
-- Target: production schema at Flyway V2 before the V3 identity migration.

SELECT 'database' AS metric, DATABASE() AS value;
SELECT 'mysql_version' AS metric, VERSION() AS value;

SELECT 'schema_table_count' AS metric, COUNT(*) AS value
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE';

SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT 'users_total' AS metric, COUNT(*) AS value FROM users;

SELECT provider, status, role, COUNT(*) AS user_count
FROM users
GROUP BY provider, status, role
ORDER BY provider, status, role;

SELECT 'blank_email_rows' AS metric, COUNT(*) AS value
FROM users
WHERE email IS NULL OR TRIM(email) = '';

SELECT 'normalized_email_duplicate_groups' AS metric, COUNT(*) AS value
FROM (
    SELECT LOWER(TRIM(email)) AS normalized_email
    FROM users
    GROUP BY LOWER(TRIM(email))
    HAVING COUNT(*) > 1
) duplicates;

SELECT 'blank_provider_rows' AS metric, COUNT(*) AS value
FROM users
WHERE provider IS NULL OR TRIM(provider) = '';

SELECT 'social_identity_missing_subject_rows' AS metric, COUNT(*) AS value
FROM users
WHERE provider <> 'LOCAL'
  AND (provider_user_id IS NULL OR TRIM(provider_user_id) = '');

SELECT 'provider_subject_duplicate_groups' AS metric, COUNT(*) AS value
FROM (
    SELECT provider, provider_user_id
    FROM users
    WHERE provider_user_id IS NOT NULL
      AND TRIM(provider_user_id) <> ''
    GROUP BY provider, provider_user_id
    HAVING COUNT(*) > 1
) duplicates;

SELECT 'local_password_missing_rows' AS metric, COUNT(*) AS value
FROM users
WHERE provider = 'LOCAL'
  AND (password_hash IS NULL OR TRIM(password_hash) = '');

SELECT 'social_password_present_rows' AS metric, COUNT(*) AS value
FROM users
WHERE provider <> 'LOCAL'
  AND password_hash IS NOT NULL
  AND TRIM(password_hash) <> '';

SELECT 'email_verification_tokens_total' AS metric, COUNT(*) AS value
FROM email_verification_tokens;

SELECT 'email_verification_orphan_rows' AS metric, COUNT(*) AS value
FROM email_verification_tokens token
LEFT JOIN users user_account ON user_account.id = token.user_id
WHERE user_account.id IS NULL;

SELECT table_name, column_name, column_type, is_nullable, column_default, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('users', 'email_verification_tokens')
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('users', 'email_verification_tokens')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND table_name IN ('users', 'email_verification_tokens')
ORDER BY table_name, constraint_name;
