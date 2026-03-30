-- V1__init_schema.sql
-- MySQL 8.x 기준 초기 스키마

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    position VARCHAR(100) NULL,
    tech_stack TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_categories_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE phases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_phases_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE periods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phase_id BIGINT NOT NULL,
    label VARCHAR(100) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_periods_phase_id FOREIGN KEY (phase_id) REFERENCES phases (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roadmap_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    phase_id BIGINT NOT NULL,
    period_id BIGINT NULL,
    assignee_user_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    due_date DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_tasks_category_id FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_tasks_phase_id FOREIGN KEY (phase_id) REFERENCES phases (id),
    CONSTRAINT fk_tasks_period_id FOREIGN KEY (period_id) REFERENCES periods (id),
    CONSTRAINT fk_tasks_assignee_user_id FOREIGN KEY (assignee_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    progress_percent TINYINT NOT NULL,
    note VARCHAR(1000) NULL,
    changed_by BIGINT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_task_progress_percent CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT fk_task_progress_task_id FOREIGN KEY (task_id) REFERENCES roadmap_tasks (id),
    CONSTRAINT fk_task_progress_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_completion_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_task_completion_items_status CHECK (status IN ('COMPLETE', 'INCOMPLETE')),
    CONSTRAINT fk_task_completion_items_task_id FOREIGN KEY (task_id) REFERENCES roadmap_tasks (id),
    CONSTRAINT fk_task_completion_items_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_memos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    author_user_id BIGINT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_task_memos_task_id FOREIGN KEY (task_id) REFERENCES roadmap_tasks (id),
    CONSTRAINT fk_task_memos_author_user_id FOREIGN KEY (author_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 조회 성능용 인덱스
CREATE INDEX idx_periods_phase_id ON periods (phase_id);

CREATE INDEX idx_tasks_category_id ON roadmap_tasks (category_id);
CREATE INDEX idx_tasks_phase_id ON roadmap_tasks (phase_id);
CREATE INDEX idx_tasks_period_id ON roadmap_tasks (period_id);
CREATE INDEX idx_tasks_assignee_user_id ON roadmap_tasks (assignee_user_id);
CREATE INDEX idx_tasks_status ON roadmap_tasks (status);
CREATE INDEX idx_tasks_due_date ON roadmap_tasks (due_date);

CREATE INDEX idx_task_progress_task_id ON task_progress (task_id);
CREATE INDEX idx_task_progress_changed_at ON task_progress (changed_at);

CREATE INDEX idx_task_completion_items_task_id ON task_completion_items (task_id);
CREATE INDEX idx_task_completion_items_status ON task_completion_items (status);

CREATE INDEX idx_task_memos_task_id ON task_memos (task_id);
CREATE INDEX idx_task_memos_author_user_id ON task_memos (author_user_id);