-- 在售盲盒商品与演示二维码：原先由应用启动时的 ApplicationRunner 写入，改为迁移管理。
INSERT INTO mall_product (id, name, subtitle, description, price_cents, image_url, sort_order, enabled, is_test)
SELECT 'paper-cut-blindbox', '林小蛙剪纸盲盒', '满族剪纸 × 长白山林蛙',
       '手工满族剪纸盲盒，随机一只林蛙角色；盒内含唯一二维码，扫码可在小程序解锁该角色的故事与祝福语，每笔销售按比例计提公益基金。',
       2990, NULL, 0, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE id = 'paper-cut-blindbox');

INSERT INTO unlock_code (code, frog_id, is_test)
SELECT 'PAPER-FROG-2026-0001', 'forest', 1
WHERE NOT EXISTS (SELECT 1 FROM unlock_code WHERE code = 'PAPER-FROG-2026-0001');

INSERT INTO unlock_code (code, frog_id, is_test)
SELECT 'PAPER-FROG-2026-0002', 'hibernation', 1
WHERE NOT EXISTS (SELECT 1 FROM unlock_code WHERE code = 'PAPER-FROG-2026-0002');

INSERT INTO unlock_code (code, frog_id, is_test)
SELECT 'PAPER-FROG-2026-0003', 'ginseng', 1
WHERE NOT EXISTS (SELECT 1 FROM unlock_code WHERE code = 'PAPER-FROG-2026-0003');
