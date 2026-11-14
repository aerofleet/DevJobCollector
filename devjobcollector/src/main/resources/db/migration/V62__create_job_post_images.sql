CREATE TABLE job_post_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_post_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    position_index INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_job_post_image_position (job_post_id, position_index),
    CONSTRAINT fk_job_post_image_job FOREIGN KEY (job_post_id) REFERENCES job_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
