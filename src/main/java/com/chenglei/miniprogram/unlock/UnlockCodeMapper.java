package com.chenglei.miniprogram.unlock;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 盲盒二维码码池访问。selectByCodeForUpdate 以行锁锁定待核销的码，
 * markRedeemed 仅在 status=unused 时生效，双保险防止同一码被并发重复核销。
 * 核销归属直接记在 unlock_code.redeemed_by / redeemed_at 上，不再另写审计表。
 */
@Mapper
public interface UnlockCodeMapper {

    @Select("SELECT id, code, frog_id AS \"frogId\", status FROM unlock_code WHERE code=#{code} FOR UPDATE")
    Map<String, Object> selectByCodeForUpdate(@Param("code") String code);

    @Update("UPDATE unlock_code SET status='redeemed', redeemed_by=#{userId}, redeemed_at=CURRENT_TIMESTAMP(3) "
        + "WHERE id=#{id} AND status='unused'")
    int markRedeemed(@Param("id") long id, @Param("userId") String userId);
}
