package com.chenglei.miniprogram.auth;

import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * app_user / user_session 访问：登录建户、会话签发与校验、资料修改。
 * user_session 只存 token 的 SHA-256 摘要，明文 token 仅在登录响应中出现一次。
 */
@Mapper
public interface UserAuthMapper {

    @Select("SELECT id, openid, nickname, avatar_url AS avatarUrl, status FROM app_user WHERE openid=#{openid}")
    Map<String, Object> selectByOpenid(@Param("openid") String openid);

    @Insert("INSERT INTO app_user (openid, unionid, nickname, avatar_url) "
        + "VALUES (#{openid}, #{unionid}, #{nickname}, #{avatarUrl})")
    int insertUser(@Param("openid") String openid, @Param("unionid") String unionid,
        @Param("nickname") String nickname, @Param("avatarUrl") String avatarUrl);

    @Update("UPDATE app_user SET unionid=#{unionid}, updated_at=CURRENT_TIMESTAMP(3) "
        + "WHERE openid=#{openid} AND (unionid IS NULL OR unionid <> #{unionid})")
    int updateUnionid(@Param("openid") String openid, @Param("unionid") String unionid);

    @Select("SELECT id, nickname, avatar_url AS avatarUrl FROM app_user "
        + "WHERE id=#{userId} AND status=1 AND deleted=0")
    Map<String, Object> selectById(@Param("userId") long userId);

    @Update("<script>UPDATE app_user SET updated_at=CURRENT_TIMESTAMP(3) "
        + "<if test='nickname != null'>, nickname=#{nickname}</if> "
        + "<if test='avatarUrl != null'>, avatar_url=#{avatarUrl}</if> "
        + "WHERE id=#{userId} AND status=1 AND deleted=0</script>")
    int updateProfile(@Param("userId") long userId, @Param("nickname") String nickname,
        @Param("avatarUrl") String avatarUrl);

    @Insert("INSERT INTO user_session (token_hash, user_id, expires_at) "
        + "VALUES (#{tokenHash}, #{userId}, #{expiresAt})")
    int insertSession(@Param("tokenHash") String tokenHash, @Param("userId") long userId,
        @Param("expiresAt") Instant expiresAt);

    @Select("SELECT s.user_id AS userId, u.nickname AS nickname, u.avatar_url AS avatarUrl "
        + "FROM user_session s JOIN app_user u ON u.id=s.user_id "
        + "WHERE s.token_hash=#{tokenHash} AND s.expires_at > CURRENT_TIMESTAMP(3) "
        + "AND u.status=1 AND u.deleted=0")
    Map<String, Object> selectSessionUser(@Param("tokenHash") String tokenHash);

    @Delete("DELETE FROM user_session WHERE expires_at < CURRENT_TIMESTAMP(3)")
    int deleteExpiredSessions();
}
