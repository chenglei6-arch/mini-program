package com.chenglei.miniprogram.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContentCatalogMapper {

    @Select("SELECT COUNT(*) FROM content_frog WHERE is_test=1")
    int countTestFrogs();

    @Select("<script>SELECT id,name,short_name AS \"shortName\",pattern,blessing,"
        + "asset_url AS \"assetUrl\" FROM content_frog WHERE enabled=1 "
        + "<if test='includeTestData == false'>AND is_test=0 </if>ORDER BY sort_order</script>")
    List<Map<String, Object>> selectFrogs(@Param("includeTestData") boolean includeTestData);

    @Select("<script>SELECT id,name,short_name AS \"shortName\",pattern,blessing,"
        + "asset_url AS \"assetUrl\" FROM content_frog WHERE id=#{frogId} AND enabled=1 "
        + "<if test='includeTestData == false'>AND is_test=0 </if></script>")
    Map<String, Object> selectFrog(@Param("frogId") String frogId,
        @Param("includeTestData") boolean includeTestData);

    @Select("<script>SELECT id,title,subtitle,path FROM content_game WHERE enabled=1 "
        + "<if test='includeTestData == false'>AND is_test=0 </if>ORDER BY sort_order</script>")
    List<Map<String, Object>> selectGames(@Param("includeTestData") boolean includeTestData);

    @Select("SELECT brand,slogan FROM home_config WHERE id=1 AND is_test=0")
    Map<String, String> selectHomeConfig();

    @Select("SELECT fund_amount AS \"fundAmount\",updated_at AS \"updatedAt\" "
        + "FROM welfare_summary WHERE id=1 AND is_test=0")
    Map<String, String> selectWelfare();

    @Select("SELECT id,title,summary,published_at AS \"publishedAt\" FROM welfare_report "
        + "WHERE is_test=0 ORDER BY published_at DESC")
    List<WelfareReportRow> selectWelfareReports();

    /** published_at 由 MyBatis 的 InstantTypeHandler 统一转换，不依赖驱动的 TIMESTAMP 返回类型。 */
    class WelfareReportRow {
        private String id;
        private String title;
        private String summary;
        private Instant publishedAt;

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public Instant getPublishedAt() {
            return publishedAt;
        }
    }
}
