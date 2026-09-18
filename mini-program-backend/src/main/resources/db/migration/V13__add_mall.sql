-- 商城（盲盒下单）：商品目录与订单。金额一律以“分”存整数，避免浮点误差。
-- 库存、微信支付、退款、物流尚未接入（见前端 docs/phase-1.md 缺口清单），
-- 订单创建后停留在 PENDING_PAYMENT，由运营侧推进。
CREATE TABLE mall_product (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    subtitle VARCHAR(256) NULL,
    description TEXT NULL,
    price_cents INT NOT NULL,
    image_url VARCHAR(1024) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    is_test TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE mall_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    unit_price_cents INT NOT NULL,
    quantity INT NOT NULL,
    total_cents INT NOT NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    receiver_address VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    idempotency_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_mall_order_no UNIQUE (order_no),
    -- 同一用户同一幂等键只允许一单，重试返回已有订单。
    CONSTRAINT uk_mall_order_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_mall_order_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_mall_order_product FOREIGN KEY (product_id) REFERENCES mall_product (id)
);

CREATE INDEX idx_mall_order_user ON mall_order (user_id, id);
