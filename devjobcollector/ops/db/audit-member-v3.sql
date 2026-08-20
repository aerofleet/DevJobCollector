-- DJC member/auth V3 cutover audit (read-only, aggregate output only)
-- Target: Flyway V3 schema after legacy identity/profile backfill.
-- Privacy: never emit row-level email, provider subject, password hash, or token data.

SELECT 'users_total' AS metric, COUNT(*) AS value FROM users
UNION ALL
SELECT 'identity_total', COUNT(*) FROM user_identities
UNION ALL
SELECT 'profile_total', COUNT(*) FROM personal_profiles
UNION ALL
SELECT 'legacy_consent_rows', COUNT(*) FROM user_consents
UNION ALL
SELECT 'legacy_unsupported_provider_count', COUNT(*)
FROM users
WHERE provider NOT IN ('LOCAL', 'GOOGLE', 'GITHUB')
UNION ALL
SELECT 'legacy_local_password_missing_count', COUNT(*)
FROM users
WHERE provider = 'LOCAL'
  AND (password_hash IS NULL OR TRIM(password_hash) = '')
UNION ALL
SELECT 'legacy_social_subject_missing_count', COUNT(*)
FROM users
WHERE provider IN ('GOOGLE', 'GITHUB')
  AND (provider_user_id IS NULL OR TRIM(provider_user_id) = '')
UNION ALL
SELECT 'user_missing_identity_count', COUNT(*)
FROM users user_account
WHERE NOT EXISTS (
    SELECT 1
    FROM user_identities identity
    WHERE identity.user_id = user_account.id
)
UNION ALL
SELECT 'identity_orphan_count', COUNT(*)
FROM user_identities identity
LEFT JOIN users user_account ON user_account.id = identity.user_id
WHERE user_account.id IS NULL
UNION ALL
SELECT 'identity_provider_subject_duplicate_count', COALESCE(SUM(duplicate_count - 1), 0)
FROM (
    SELECT COUNT(*) AS duplicate_count
    FROM user_identities
    GROUP BY provider, provider_subject
    HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'identity_user_provider_duplicate_count', COALESCE(SUM(duplicate_count - 1), 0)
FROM (
    SELECT COUNT(*) AS duplicate_count
    FROM user_identities
    GROUP BY user_id, provider
    HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'legacy_primary_identity_mismatch_count', COUNT(*)
FROM users user_account
JOIN user_identities identity ON identity.user_id = user_account.id
WHERE identity.provider <> user_account.provider
   OR NOT (
       BINARY identity.provider_subject <=> BINARY CASE
           WHEN user_account.provider = 'LOCAL' THEN LOWER(TRIM(user_account.email))
           ELSE user_account.provider_user_id
       END
   )
UNION ALL
SELECT 'identity_invalid_provider_count', COUNT(*)
FROM user_identities
WHERE provider NOT IN ('LOCAL', 'GOOGLE', 'GITHUB', 'KAKAO', 'NAVER', 'APPLE')
UNION ALL
SELECT 'profile_orphan_count', COUNT(*)
FROM personal_profiles profile
LEFT JOIN users user_account ON user_account.id = profile.user_id
WHERE user_account.id IS NULL
UNION ALL
SELECT 'active_user_missing_profile_count', COUNT(*)
FROM users user_account
WHERE user_account.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM personal_profiles profile
      WHERE profile.user_id = user_account.id
  )
UNION ALL
SELECT 'non_active_user_with_profile_count', COUNT(*)
FROM personal_profiles profile
JOIN users user_account ON user_account.id = profile.user_id
WHERE user_account.status <> 'ACTIVE'
UNION ALL
SELECT 'profile_invalid_status_count', COUNT(*)
FROM personal_profiles
WHERE profile_status NOT IN ('ACTIVE', 'PRIVATE', 'DELETED')
UNION ALL
SELECT 'consent_orphan_count', COUNT(*)
FROM user_consents consent
LEFT JOIN users user_account ON user_account.id = consent.user_id
WHERE user_account.id IS NULL
UNION ALL
SELECT 'consent_invalid_type_count', COUNT(*)
FROM user_consents
WHERE consent_type NOT IN (
    'TERMS_OF_SERVICE',
    'PRIVACY_POLICY',
    'MARKETING',
    'PERSONAL_DATA_COLLECTION'
)
UNION ALL
SELECT 'consent_invalid_action_count', COUNT(*)
FROM user_consents
WHERE action NOT IN ('ACCEPTED', 'REVOKED')
UNION ALL
SELECT 'consent_blank_policy_version_count', COUNT(*)
FROM user_consents
WHERE TRIM(policy_version) = '';
