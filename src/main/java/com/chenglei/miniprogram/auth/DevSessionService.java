package com.chenglei.miniprogram.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Small in-memory session store for local integration. Replace with the real
 * WeChat/session persistence implementation before production deployment.
 */
@Service
public class DevSessionService {

    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public Session login(String code, String nickname, String avatarUrl) {
        String token = "dev-" + UUID.randomUUID();
        User user = new User("local-user", nickname == null || nickname.isBlank() ? "体验用户" : nickname,
            avatarUrl == null ? "" : avatarUrl, true);
        sessions.put(token, user);
        return new Session(token, user);
    }

    public User findUser(String token) {
        return token == null ? null : sessions.get(token);
    }

    public record User(String id, String nickname, String avatarUrl, boolean isGuest) { }

    public record Session(String accessToken, User user) { }
}
