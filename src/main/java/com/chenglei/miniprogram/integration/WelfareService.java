package com.chenglei.miniprogram.integration;

import java.time.ZoneId;
import java.util.ArrayList;
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
        for (ContentCatalogMapper.WelfareReportRow row : content.welfareReports()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("title", row.getTitle());
            item.put("summary", row.getSummary());
            item.put("date", row.getPublishedAt().atZone(ZONE).toLocalDate().toString());
            reports.add(item);
        }
        response.put("reports", reports);
        return response;
    }
}
