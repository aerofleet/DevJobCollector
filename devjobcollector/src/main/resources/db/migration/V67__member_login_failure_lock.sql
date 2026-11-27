ALTER TABLE users ADD COLUMN login_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN login_failure_window_start DATETIME(6) NULL,
    ADD COLUMN login_locked_until DATETIME(6) NULL,
    ADD COLUMN verification_sent_at DATETIME(6) NULL;
