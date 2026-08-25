CREATE TABLE resumes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    resume_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    content_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_resumes_user_updated (user_id, updated_at, id),
    CONSTRAINT fk_resumes_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_post_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_bookmarks_user_job (user_id, job_post_id),
    KEY idx_job_bookmarks_job_post (job_post_id),
    KEY idx_job_bookmarks_user_created (user_id, created_at, id),
    CONSTRAINT fk_job_bookmarks_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_bookmarks_job_post_id
        FOREIGN KEY (job_post_id) REFERENCES job_posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_view_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_post_id BIGINT NOT NULL,
    first_viewed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_viewed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    view_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_view_history_user_job (user_id, job_post_id),
    KEY idx_job_view_history_job_post (job_post_id),
    KEY idx_job_view_history_user_recent (user_id, last_viewed_at, id),
    CONSTRAINT fk_job_view_history_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_view_history_job_post_id
        FOREIGN KEY (job_post_id) REFERENCES job_posts (id) ON DELETE CASCADE,
    CONSTRAINT chk_job_view_history_view_count CHECK (view_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_post_id BIGINT NOT NULL,
    application_status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    memo VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_applications_user_job (user_id, job_post_id),
    KEY idx_applications_job_post (job_post_id),
    KEY idx_applications_user_status_updated (user_id, application_status, updated_at, id),
    CONSTRAINT fk_applications_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_job_post_id
        FOREIGN KEY (job_post_id) REFERENCES job_posts (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
