CREATE TABLE growth_events (
    event_id CHAR(36) NOT NULL PRIMARY KEY,
    visitor_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    page VARCHAR(20) NOT NULL,
    job_id BIGINT NULL,
    source VARCHAR(48) NOT NULL,
    medium VARCHAR(48) NOT NULL,
    campaign VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_growth_created (created_at),
    INDEX idx_growth_visitor_created (visitor_id, created_at),
    INDEX idx_growth_session (session_id, kind)
);
