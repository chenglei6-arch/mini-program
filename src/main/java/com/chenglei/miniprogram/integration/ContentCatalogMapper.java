package com.chenglei.miniprogram.integration;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContentCatalogMapper {

    @Select("SELECT COUNT(*) FROM content_frog WHERE is_test=0")
    int countFrogs();

    @Select("SELECT COUNT(*) FROM content_frog WHERE is_test=1")
    int countTestFrogs();

    @Insert("INSERT INTO content_frog (id,name,short_name,pattern,blessing,asset_url,source_url,sort_order,enabled) VALUES (#{id},#{name},#{shortName},#{pattern},#{blessing},#{assetUrl},#{sourceUrl},#{sortOrder},1)")
    int insertFrog(@Param("id") String id, @Param("name") String name, @Param("shortName") String shortName,
        @Param("pattern") String pattern, @Param("blessing") String blessing, @Param("assetUrl") String assetUrl,
        @Param("sourceUrl") String sourceUrl, @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT id,name,short_name AS shortName,pattern,blessing,asset_url AS assetUrl,source_url AS sourceUrl FROM content_frog WHERE enabled=1 <if test='includeTestData == false'>AND is_test=0 </if>ORDER BY sort_order</script>")
    List<Map<String, Object>> selectFrogs(@Param("includeTestData") boolean includeTestData);

    @Select("<script>SELECT id,name,short_name AS shortName,pattern,blessing,asset_url AS assetUrl,source_url AS sourceUrl FROM content_frog WHERE id=#{frogId} AND enabled=1 <if test='includeTestData == false'>AND is_test=0 </if></script>")
    Map<String, Object> selectFrog(@Param("frogId") String frogId, @Param("includeTestData") boolean includeTestData);

    @Insert("INSERT INTO content_frog_section (frog_id,heading,sort_order) VALUES (#{frogId},#{heading},#{sortOrder})")
    int insertSection(@Param("frogId") String frogId, @Param("heading") String heading, @Param("sortOrder") int sortOrder);

    @Select("SELECT id FROM content_frog_section WHERE frog_id=#{frogId} AND sort_order=#{sortOrder}")
    Long selectSectionId(@Param("frogId") String frogId, @Param("sortOrder") int sortOrder);

    @Insert("INSERT INTO content_frog_paragraph (section_id,content,sort_order) VALUES (#{sectionId},#{content},#{sortOrder})")
    int insertParagraph(@Param("sectionId") Long sectionId, @Param("content") String content, @Param("sortOrder") int sortOrder);

    @Select("SELECT id,heading FROM content_frog_section WHERE frog_id=#{frogId} ORDER BY sort_order")
    List<Map<String, Object>> selectSections(@Param("frogId") String frogId);

    @Select("SELECT content FROM content_frog_paragraph WHERE section_id=#{sectionId} ORDER BY sort_order")
    List<String> selectParagraphs(@Param("sectionId") Long sectionId);

    @Insert("INSERT INTO content_game (id,title,subtitle,path,sort_order,enabled) VALUES (#{id},#{title},#{subtitle},#{path},#{sortOrder},1)")
    int insertGame(@Param("id") String id, @Param("title") String title, @Param("subtitle") String subtitle,
        @Param("path") String path, @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT id,title,subtitle,path FROM content_game WHERE enabled=1 <if test='includeTestData == false'>AND is_test=0 </if>ORDER BY sort_order</script>")
    List<Map<String, Object>> selectGames(@Param("includeTestData") boolean includeTestData);

    @Insert("INSERT INTO content_pattern (name,sort_order,enabled) VALUES (#{name},#{sortOrder},1)")
    int insertPattern(@Param("name") String name, @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT name FROM content_pattern WHERE enabled=1 <if test='includeTestData == false'>AND is_test=0 </if>ORDER BY sort_order</script>")
    List<String> selectPatterns(@Param("includeTestData") boolean includeTestData);

    @Insert("INSERT INTO content_badge (id,name,level,sort_order,enabled) VALUES (#{id},#{name},#{level},#{sortOrder},1)")
    int insertBadge(@Param("id") String id, @Param("name") String name, @Param("level") String level,
        @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT id,name,level FROM content_badge WHERE enabled=1 <if test='includeTestData == false'>AND is_test=0 </if>ORDER BY sort_order</script>")
    List<Map<String, Object>> selectBadges(@Param("includeTestData") boolean includeTestData);

    @Insert("INSERT INTO home_config (id,brand,slogan,fund_amount,fund_updated_at) VALUES (1,#{brand},#{slogan},#{fundAmount},#{fundUpdatedAt})")
    int insertHomeConfig(@Param("brand") String brand, @Param("slogan") String slogan,
        @Param("fundAmount") String fundAmount, @Param("fundUpdatedAt") String fundUpdatedAt);

    @Select("SELECT brand,slogan,fund_amount AS fundAmount,fund_updated_at AS fundUpdateAt FROM home_config WHERE id=1 AND is_test=0")
    Map<String, String> selectHomeConfig();

    @Insert("INSERT INTO home_activity (content,sort_order,enabled) VALUES (#{content},#{sortOrder},1)")
    int insertActivity(@Param("content") String content, @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT content FROM home_activity WHERE enabled=1 <if test='includeTestData == false'>AND is_test=0 </if>ORDER BY sort_order</script>")
    List<String> selectActivities(@Param("includeTestData") boolean includeTestData);

    @Insert("INSERT INTO welfare_summary (id,fund_amount,updated_at) VALUES (1,#{fundAmount},#{updatedAt})")
    int insertWelfare(@Param("fundAmount") String fundAmount, @Param("updatedAt") String updatedAt);

    @Select("SELECT fund_amount AS fundAmount,updated_at AS updatedAt FROM welfare_summary WHERE id=1 AND is_test=0")
    Map<String, String> selectWelfare();
}
