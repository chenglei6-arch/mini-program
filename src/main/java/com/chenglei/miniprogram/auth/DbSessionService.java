package com.chenglei.miniprogram.auth;

import com.chenglei.miniprogram.common.db.RowValues;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 基于微信 code2session 与 user_session 表的登录实现：
 * openid 唯一对应 app_user，会话 token 只在库里存 SHA-256 摘要。
 * 未配置微信密钥时回退到固定开发账号，保证本地联调体验。
 */
@Service
public class DbSessionService implements SessionService {

    /** 开发模式的固定 openid：同一次部署内始终映射到同一个 app_user，进度可跨重启保留。 */
    static final String DEV_OPENID = "dev-local-user";
    private static final String DEFAULT_NICKNAME = "体验用户";

    private final UserAuthMapper users;
    private final WeChatApiService weChatApi;
    private final Duration accessTokenTtl;
    private final SecureRandom random = new SecureRandom();

    public DbSessionService(UserAuthMapper users, WeChatApiService weChatApi,
        @Value("${app.auth.access-token-ttl-seconds:7200}") long accessTokenTtlSeconds) {
        this.users = users;
        this.weChatApi = weChatApi;
        this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
    }

    // 不加事务：微信 HTTP 调用可能长达数秒，事务会把连接池中的连接一直占着。
    // 各语句均为单条自动提交，并发首登的竞态由 openid 唯一键兜底（见 resolveUserId）。
    @Override
    public Session login(String code, String nickname, String avatarUrl) {
        String openid;
        String unionid = null;
        if (weChatApi.isConfigured()) {
            WeChatApiService.CodeSession result = weChatApi.exchangeCode(code);
            openid = result.openid();
            unionid = result.unionid();
        } else {
            openid = DEV_OPENID;
        }

        long userId = resolveUserId(openid, unionid, nickname, avatarUrl);
        CurrentUser user = profileOf(String.valueOf(userId));
        String token = newToken();
        users.insertSession(sha256Hex(token), userId, Instant.now().plus(accessTokenTtl));
        return new Session(token, user);
    }

    @Override
    public CurrentUser findByToken(String token) {
        if (token == null || token.isBlank()) return null;
        Map<String, Object> row = users.selectSessionUser(sha256Hex(token.trim()));
        if (row == null) return null;
        return new CurrentUser(String.valueOf(RowValues.number(row, "userId")),
            (String) RowValues.valueOf(row, "nickname"),
            (String) RowValues.valueOf(row, "avatarUrl"), false);
    }

    @Override
    public CurrentUser profileOf(String userId) {
        Map<String, Object> row = users.selectById(parseUserId(userId));
        if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        return new CurrentUser(String.valueOf(RowValues.number(row, "id")),
            (String) RowValues.valueOf(row, "nickname"), (String) RowValues.valueOf(row, "avatarUrl"), false);
    }

    @Override
    public void updateProfile(String userId, String nickname, String avatarUrl) {
        users.updateProfile(parseUserId(userId), nickname, avatarUrl);
    }

    /** 每天清理过期会话，避免 user_session 无限增长。 */
    @Scheduled(cron = "0 32 4 * * *")
    public void purgeExpiredSessions() {
        users.deleteExpiredSessions();
    }

    private long resolveUserId(String openid, String unionid, String nickname, String avatarUrl) {
        Map<String, Object> existing = users.selectByOpenid(openid);
        if (existing == null) {
            String effectiveNickname = nickname == null || nickname.isBlank() ? DEFAULT_NICKNAME : nickname;
            try {
                users.insertUser(openid, unionid, effectiveNickname, avatarUrl);
            } catch (DuplicateKeyException e) {
                // 并发首登：openid 唯一键冲突说明另一事务已插入，走复用分支。
            }
            existing = users.selectByOpenid(openid);
            if (existing == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登录用户创建失败");
        } else if (unionid != null) {
            users.updateUnionid(openid, unionid);
        }
        long userId = RowValues.number(existing, "id");
        if ((nickname != null && !nickname.isBlank()) || avatarUrl != null) {
            users.updateProfile(userId, nickname == null || nickname.isBlank() ? null : nickname, avatarUrl);
        }
        return userId;
    }

    private long parseUserId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }
}
