CREATE TABLE security_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    subject_user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    previous_value VARCHAR(50) DEFAULT NULL,
    new_value VARCHAR(50) DEFAULT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_security_audit_company_time (company_id, occurred_at, id),
    KEY idx_security_audit_actor_time (actor_user_id, occurred_at, id),
    KEY idx_security_audit_type_time (event_type, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
