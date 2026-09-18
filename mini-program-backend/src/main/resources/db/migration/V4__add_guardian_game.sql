CREATE TABLE guardian_daily_state (
    user_id VARCHAR(128) NOT NULL,
    game_date DATE NOT NULL,
    draws_used INT NOT NULL DEFAULT 0,
    share_bonus_claimed TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, game_date)
);

CREATE TABLE guardian_collection (
    user_id VARCHAR(128) NOT NULL,
    frog_id VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, frog_id),
    CONSTRAINT fk_guardian_collection_frog FOREIGN KEY (frog_id) REFERENCES content_frog (id)
);

CREATE TABLE guardian_round (
    user_id VARCHAR(128) NOT NULL PRIMARY KEY,
    round_id VARCHAR(64) NOT NULL,
    frog_id VARCHAR(64) NOT NULL,
    options_json TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_guardian_round_id UNIQUE (round_id),
    CONSTRAINT fk_guardian_round_frog FOREIGN KEY (frog_id) REFERENCES content_frog (id)
);

CREATE TABLE guardian_event (
    user_id VARCHAR(128) NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, event_key)
);

CREATE INDEX idx_guardian_collection_user ON guardian_collection (user_id, unlocked_at);

INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'guardian-messenger', '守护神使者', 'silver', 4, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'guardian-messenger');

INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'guardian-nine', '九蛙守护者', 'gold', 8, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'guardian-nine');
