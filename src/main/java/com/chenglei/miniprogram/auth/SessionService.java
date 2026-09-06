package com.chenglei.miniprogram.auth;

/**
 * 登录会话能力。默认实现 {@link DbSessionService} 基于微信 code2session 与
 * user_session 表；无数据库联调模式（app.storage.mode=in-memory）回退到内存会话。
 */
public interface SessionService {

    /**
     * 以 wx.login 获取的 code 换取会话。nickname/avatarUrl 来自前端头像昵称填写能力，可为空。
     */
    Session login(String code, String nickname, String avatarUrl);

    /** 按 Bearer token 解析用户；token 无效或过期返回 null。 */
    CurrentUser findByToken(String token);

    /** 读取用户资料（昵称/头像），保证 PATCH 之后的读请求返回最新值。 */
    CurrentUser profileOf(String userId);

    /** 更新昵称与头像；传 null 的字段保持原值。 */
    void updateProfile(String userId, String nickname, String avatarUrl);

    record Session(String accessToken, CurrentUser user) { }
}
