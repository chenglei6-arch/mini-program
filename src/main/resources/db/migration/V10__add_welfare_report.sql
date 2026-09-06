-- 公益资金公示报告（按季度发布）。竞赛演示阶段预置两期示例报告，
-- 上线前由运营方替换为真实公示内容或清空。
CREATE TABLE welfare_report (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    period VARCHAR(32) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    published_at TIMESTAMP(3) NOT NULL,
    is_test TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

INSERT INTO welfare_report (id, title, period, summary, published_at, is_test)
SELECT 'welfare-report-2026q2', '2026年第二季度资金使用公示', '2026-Q2',
       '本期基金用于满族剪纸传承人创作补贴与非遗进校园活动，资助传承人 2 名，进校园 3 场，参与学生 620 人。',
       '2026-07-05 10:00:00', 0
WHERE NOT EXISTS (SELECT 1 FROM welfare_report WHERE id = 'welfare-report-2026q2');

INSERT INTO welfare_report (id, title, period, summary, published_at, is_test)
SELECT 'welfare-report-2026q3', '2026年第三季度资金使用公示', '2026-Q3',
       '本期基金用于满族说部数字化记录与传承人作品捐赠，完成说部录音 12 段，向乡村学校捐赠剪纸作品 40 幅。',
       '2026-10-05 10:00:00', 0
WHERE NOT EXISTS (SELECT 1 FROM welfare_report WHERE id = 'welfare-report-2026q3');
