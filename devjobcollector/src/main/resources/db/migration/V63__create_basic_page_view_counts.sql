CREATE TABLE basic_page_view_counts (
    view_date DATE NOT NULL,
    page VARCHAR(24) NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (view_date, page)
);
