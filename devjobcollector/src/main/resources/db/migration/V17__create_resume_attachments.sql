CREATE TABLE resume_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size INT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_resume_attachment (resume_id, id),
    CONSTRAINT fk_resume_attachment_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);
