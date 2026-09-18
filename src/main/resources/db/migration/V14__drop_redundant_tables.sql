-- 下线只写不读与不再需要的表：
-- 1. unlock_record：只写不读，核销归属已由 unlock_code.redeemed_by/redeemed_at 记录；
-- 2. home_activity：首页“蛙友动态”只展示真实徽章解锁，演示文案不再入库；
-- 3. content_pattern：前端不消费该目录；
-- 4. content_frog_section / content_frog_paragraph：设计说明改由 classpath 内容文件提供；
-- 5. content_badge：徽章目录改由 classpath 单一目录文件提供，先解除 user_badge 外键。
ALTER TABLE user_badge DROP CONSTRAINT fk_user_badge_badge;

DROP TABLE IF EXISTS unlock_record;
DROP TABLE IF EXISTS home_activity;
DROP TABLE IF EXISTS content_pattern;
DROP TABLE IF EXISTS content_frog_paragraph;
DROP TABLE IF EXISTS content_frog_section;
DROP TABLE IF EXISTS content_badge;
