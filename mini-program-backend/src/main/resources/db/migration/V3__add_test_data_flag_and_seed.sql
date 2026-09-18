ALTER TABLE content_frog ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE content_game ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE content_pattern ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE content_badge ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE home_config ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE home_activity ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE welfare_summary ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;

INSERT INTO content_frog
    (id, name, short_name, pattern, blessing, asset_url, source_url, sort_order, enabled, is_test)
VALUES
    ('test-frog-001', '林蛙测试数据-001', '测', '测试纹样', '林蛙测试寓意-001',
     '/assets/frogs/forest.png', '/assets/sources/frogs/forest.docx', 9000, 1, 1);

INSERT INTO content_game
    (id, title, subtitle, path, sort_order, enabled, is_test)
VALUES
    ('test-game-001', '游戏测试数据-001', '游戏测试副标题-001',
     '/pages/dev/test-game', 9000, 1, 1);

INSERT INTO content_pattern (name, sort_order, enabled, is_test)
VALUES ('纹样测试数据-001', 9000, 1, 1);

INSERT INTO content_badge (id, name, level, sort_order, enabled, is_test)
VALUES ('test-badge-001', '徽章测试数据-001', 'test', 9000, 1, 1);

INSERT INTO home_config (id, brand, slogan, fund_amount, fund_updated_at, is_test)
VALUES (2, '首页测试数据-001', '首页测试标语-001', '0.00', '测试时间-001', 1);

INSERT INTO home_activity (content, sort_order, enabled, is_test)
VALUES ('动态测试数据-001', 9000, 1, 1);

INSERT INTO welfare_summary (id, fund_amount, updated_at, is_test)
VALUES (2, '0.00', '公益测试数据-001', 1);
