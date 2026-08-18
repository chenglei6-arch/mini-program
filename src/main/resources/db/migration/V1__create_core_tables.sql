CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(64) NOT NULL,
    unionid VARCHAR(64) NULL,
    nickname VARCHAR(64) NULL,
    avatar_url VARCHAR(512) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_app_user_openid UNIQUE (openid)
);

CREATE TABLE game_progress (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    game_id VARCHAR(64) NOT NULL,
    progress_json TEXT NULL,
    completed TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_game_progress_user_game UNIQUE (user_id, game_id),
    CONSTRAINT fk_game_progress_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE game_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    game_id VARCHAR(64) NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_game_event_key UNIQUE (event_key),
    CONSTRAINT fk_game_event_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_game_event_user_game ON game_event (user_id, game_id);

CREATE TABLE unlock_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    unlock_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    unlocked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_unlock_record_user_code UNIQUE (user_id, unlock_code),
    CONSTRAINT fk_unlock_record_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
