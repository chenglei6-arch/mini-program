-- 徽章解锁流水：BadgeService 按条件判定后写入，user_id 口径与守护神系列表一致。
CREATE TABLE user_badge (
    user_id VARCHAR(128) NOT NULL,
    badge_id VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, badge_id),
    CONSTRAINT fk_user_badge_badge FOREIGN KEY (badge_id) REFERENCES content_badge (id)
);

CREATE INDEX idx_user_badge_badge ON user_badge (badge_id, unlocked_at);

-- 补齐需求定义的 9 枚主徽章中缺失的 4 枚（剪纸/说部各缺白银与黄金档）。
INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'paper-craftsman', '剪纸匠人', 'silver', 5, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'paper-craftsman');

INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'story-inheritor', '说部传承人', 'silver', 6, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'story-inheritor');

INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'story-guardian', '说部守护者', 'gold', 7, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'story-guardian');

INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'paper-master', '剪纸大师', 'gold', 9, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'paper-master');
