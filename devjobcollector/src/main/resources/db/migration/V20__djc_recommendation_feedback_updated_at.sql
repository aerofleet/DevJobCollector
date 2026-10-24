ALTER TABLE ai_recommendation_feedback
    ADD COLUMN updated_at DATETIME(6) NULL;

UPDATE ai_recommendation_feedback SET updated_at=created_at;

ALTER TABLE ai_recommendation_feedback
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
