-- 内容目录改由迁移管理（原先由应用启动时读取 content-seed.json 写入，已移除）。
INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'forest', '护林蛙', '护', '山林纹', '长白山森林，象征生命与家园', '/assets/frogs/forest.png', 0, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'forest');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'ginseng', '采参蛙', '采', '云纹', '萨满通天信仰，象征吉祥与祝福', '/assets/frogs/ginseng.png', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'ginseng');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'hibernation', '冬眠蛙', '冬', '水波纹', '溪流与湿地，象征林蛙栖息环境', '/assets/frogs/hibernation.png', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'hibernation');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'lotus', '荷叶蛙', '荷', '蛙纹', '满族萨满图腾，象征繁衍与山林灵物', '/assets/frogs/lotus.png', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'lotus');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'insect', '捕虫蛙', '捕', '虫鸟纹', '森林食物链，象征自然和谐', '/assets/frogs/insect.png', 4, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'insect');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'immune', '免疫蛙', '免', '太阳纹', '萨满向日崇拜，象征光明与生命力', '/assets/frogs/immune.png', 5, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'immune');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'youth', '驻颜蛙', '驻', '花叶纹', '山林繁花，象征美好与绽放', '/assets/frogs/youth.png', 6, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'youth');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'snow', '天池映雪蛙', '雪', '冰雪纹', '长白山冰雪，象征纯洁与勇气', '/assets/frogs/snow.png', 7, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'snow');

INSERT INTO content_frog (id, name, short_name, pattern, blessing, asset_url, sort_order, enabled, is_test)
SELECT 'lung', '润肺蛙', '润', '空气纹', '长白山云雾，象征清新与呼吸', '/assets/frogs/lung.png', 8, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_frog WHERE id = 'lung');

INSERT INTO content_game (id, title, subtitle, path, sort_order, enabled, is_test)
SELECT 'story', '林蛙谷寻踪', '听一段说部，走完一段旅程', '/pages/games/story/story', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_game WHERE id = 'story');

INSERT INTO content_game (id, title, subtitle, path, sort_order, enabled, is_test)
SELECT 'guardian', '摇一摇·守护神', '解锁你的林蛙守护神', '/pages/games/guardian/guardian', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_game WHERE id = 'guardian');

INSERT INTO content_game (id, title, subtitle, path, sort_order, enabled, is_test)
SELECT 'forest-quiz', '林蛙知识闯关', '答题解锁主题徽章', '/pages/games/forest-quiz/forest-quiz', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM content_game WHERE id = 'forest-quiz');

INSERT INTO home_config (id, brand, slogan)
SELECT 1, '蛙声说部·哈什蚂传奇', '听一段说部，遇见一只灵蛙'
WHERE NOT EXISTS (SELECT 1 FROM home_config WHERE id = 1);

INSERT INTO welfare_summary (id, fund_amount, updated_at)
SELECT 1, '12,480.00', '2026-08-17'
WHERE NOT EXISTS (SELECT 1 FROM welfare_summary WHERE id = 1);
