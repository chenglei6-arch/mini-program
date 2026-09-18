package com.chenglei.miniprogram.mall;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商城商品目录访问。金额字段统一以分（priceCents）返回。 */
@Mapper
public interface MallProductMapper {

    @Select("<script>SELECT id,name,subtitle,description,price_cents AS \"priceCents\","
        + "image_url AS \"imageUrl\" "
        + "FROM mall_product WHERE enabled=1 <if test='includeTestData == false'>AND is_test=0 </if>"
        + "ORDER BY sort_order, id</script>")
    List<Map<String, Object>> selectProducts(@Param("includeTestData") boolean includeTestData);

    @Select("<script>SELECT id,name,subtitle,description,price_cents AS \"priceCents\","
        + "image_url AS \"imageUrl\" "
        + "FROM mall_product WHERE id=#{productId} AND enabled=1 "
        + "<if test='includeTestData == false'>AND is_test=0 </if></script>")
    Map<String, Object> selectProduct(@Param("productId") String productId,
        @Param("includeTestData") boolean includeTestData);
}
