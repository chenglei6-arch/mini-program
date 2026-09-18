CREATE TABLE content_frog (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    short_name VARCHAR(32) NOT NULL,
    pattern VARCHAR(128) NOT NULL,
    blessing VARCHAR(512) NOT NULL,
    asset_url VARCHAR(1024) NOT NULL,
    source_url VARCHAR(1024) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE content_frog_section (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    frog_id VARCHAR(64) NOT NULL,
    heading VARCHAR(256) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_content_frog_section_frog FOREIGN KEY (frog_id) REFERENCES content_frog (id)
);

CREATE TABLE content_frog_paragraph (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    section_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_content_frog_paragraph_section FOREIGN KEY (section_id) REFERENCES content_frog_section (id)
);

CREATE INDEX idx_content_frog_section_frog ON content_frog_section (frog_id, sort_order);
CREATE INDEX idx_content_frog_paragraph_section ON content_frog_paragraph (section_id, sort_order);

CREATE TABLE content_game (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    subtitle VARCHAR(256) NOT NULL,
    path VARCHAR(512) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE content_pattern (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE content_badge (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    level VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE home_config (
    id TINYINT NOT NULL PRIMARY KEY,
    brand VARCHAR(256) NOT NULL,
    slogan VARCHAR(256) NOT NULL,
    fund_amount VARCHAR(64) NOT NULL,
    fund_updated_at VARCHAR(64) NOT NULL
);

CREATE TABLE home_activity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content VARCHAR(512) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE welfare_summary (
    id TINYINT NOT NULL PRIMARY KEY,
    fund_amount VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);
