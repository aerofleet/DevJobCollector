CREATE TABLE naver_search_api_usage (
    period_type VARCHAR(7) NOT NULL,
    period_start DATE NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (period_type, period_start),
    CONSTRAINT chk_naver_search_api_usage_period CHECK (period_type IN ('DAILY', 'MONTHLY')),
    CONSTRAINT chk_naver_search_api_usage_count CHECK (request_count >= 0)
);
