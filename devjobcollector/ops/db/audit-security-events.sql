-- Set the target company ID in the current session before running this read-only audit.
SET @company_id = NULL;

SELECT
    event_type,
    actor_user_id,
    subject_user_id,
    company_id,
    previous_value,
    new_value,
    occurred_at
FROM security_audit_events
WHERE @company_id IS NOT NULL
  AND company_id = @company_id
ORDER BY occurred_at ASC, id ASC;
