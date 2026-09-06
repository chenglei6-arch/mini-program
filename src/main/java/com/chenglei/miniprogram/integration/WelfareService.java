package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.common.db.RowValues;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 公益与徽章体系聚合：基金池摘要 + 按季度发布的资金公示报告。
 * 报告日期统一格式化为 yyyy-MM-dd 供前端展示。
 */
@Service
public class WelfareService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ContentCatalogService content;

    public WelfareService(ContentCatalogService content) {
        this.content = content;
    }

    public Map<String, Object> summary() {
        Map<String, Object> response = new LinkedHashMap<>(content.welfare());
        List<Map<String, Object>> reports = new ArrayList<>();
        for (Map<String, Object> row : content.welfareReports()) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Object publishedAt = RowValues.valueOf(row, "publishedAt");
            item.put("date", publishedAt instanceof Date date
                ? date.toInstant().atZone(ZONE).toLocalDate().toString()
                : String.valueOf(publishedAt));
            reports.add(item);
        }
        response.put("reports", reports);
        return response;
    }
}
