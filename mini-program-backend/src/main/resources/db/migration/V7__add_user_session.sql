-- 登录会话持久化：token 只存 SHA-256 摘要，明文 token 仅返回给客户端一次。
CREATE TABLE user_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token_hash CHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_user_session_token UNIQUE (token_hash),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_user_session_expiry ON user_session (expires_at);
