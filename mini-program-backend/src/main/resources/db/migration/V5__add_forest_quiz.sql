CREATE TABLE forest_quiz_progress (
    user_id VARCHAR(128) NOT NULL PRIMARY KEY,
    completed_levels_json TEXT NOT NULL,
    current_level_id VARCHAR(64) NOT NULL,
    question_index INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE forest_quiz_event (
    user_id VARCHAR(128) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, event_key)
);

CREATE INDEX idx_forest_quiz_event_user ON forest_quiz_event (user_id, created_at);

INSERT INTO content_game (id, title, subtitle, path, sort_order, enabled, is_test)
SELECT 'forest-quiz', '林蛙知识闯关', '答题解锁主题徽章', '/pages/games/forest-quiz/forest-quiz', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_game WHERE id = 'forest-quiz');

INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'quiz-morphology', '形态徽章', 'bronze', 10, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'quiz-morphology');
INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'quiz-distribution', '分布徽章', 'bronze', 11, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'quiz-distribution');
INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'quiz-diet', '食性徽章', 'bronze', 12, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'quiz-diet');
INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'quiz-hibernation', '冬眠徽章', 'bronze', 13, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'quiz-hibernation');
INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'quiz-reproduction', '繁殖徽章', 'bronze', 14, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'quiz-reproduction');
INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
SELECT 'quiz-protection', '保护徽章', 'bronze', 15, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_badge WHERE id = 'quiz-protection');
