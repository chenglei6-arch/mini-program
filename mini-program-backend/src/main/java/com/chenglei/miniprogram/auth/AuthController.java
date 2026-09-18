package com.chenglei.miniprogram.auth;

import com.chenglei.miniprogram.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final SessionService sessions;
    private final long accessTokenTtlSeconds;

    public AuthController(SessionService sessions,
        @Value("${app.auth.access-token-ttl-seconds:7200}") long accessTokenTtlSeconds) {
        this.sessions = sessions;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    @PostMapping("/wechat-login")
    public ApiResponse<TokenData> login(@Valid @RequestBody LoginRequest request) {
        SessionService.Session session = sessions.login(request.code(), request.nickname(), request.avatarUrl());
        return ApiResponse.success(new TokenData(session.accessToken(), accessTokenTtlSeconds, session.user()));
    }

    public record LoginRequest(
        @NotBlank @Size(max = 128) String code,
        @Size(max = 64) String nickname,
        @Size(max = 512) String avatarUrl
    ) { }

    /** 到期后由小程序重新 wx.login 换新 token；没有刷新令牌这一层。 */
    public record TokenData(String accessToken, long expiresIn, CurrentUser user) { }
}
