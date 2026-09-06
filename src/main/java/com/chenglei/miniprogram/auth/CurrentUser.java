package com.chenglei.miniprogram.auth;

/**
 * 已登录用户在安全上下文中的表示。id 为 app_user 主键的字符串形式，
 * 守护神、闯关等业务表均以该字符串作为 user_id 存取。
 */
public record CurrentUser(String id, String nickname, String avatarUrl, boolean isGuest) { }
