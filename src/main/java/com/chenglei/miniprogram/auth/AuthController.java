package com.chenglei.miniprogram.auth;

import com.chenglei.miniprogram.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final DevSessionService sessions;

    public AuthController(DevSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/wechat-login")
    public ApiResponse<TokenData> login(@Valid @RequestBody LoginRequest request) {
        DevSessionService.Session session = sessions.login(request.code(), request.nickname(), request.avatarUrl());
        return ApiResponse.success(new TokenData(session.accessToken(), 7200, "refresh-" + session.accessToken(),
            2592000, session.user()));
    }

    public record LoginRequest(
        @NotBlank @Size(max = 128) String code,
        @Size(max = 64) String nickname,
        @Size(max = 512) String avatarUrl
    ) { }

    public record TokenData(String accessToken, long expiresIn, String refreshToken, long refreshExpiresIn,
                            DevSessionService.User user) { }
}
