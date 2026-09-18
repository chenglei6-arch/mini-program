-- 盲盒二维码码池：code 全局唯一，状态机 unused -> redeemed。
-- 种子演示码由应用启动时的 ContentCatalogService 播种（依赖 content_frog 先行导入）；
-- 正式码由运营批量导入并置 is_test=0。
CREATE TABLE unlock_code (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    frog_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'unused',
    redeemed_by VARCHAR(128) NULL,
    redeemed_at TIMESTAMP(3) NULL,
    is_test TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_unlock_code UNIQUE (code),
    CONSTRAINT fk_unlock_code_frog FOREIGN KEY (frog_id) REFERENCES content_frog (id)
);
