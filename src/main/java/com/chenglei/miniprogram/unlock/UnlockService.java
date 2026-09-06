package com.chenglei.miniprogram.unlock;

import com.chenglei.miniprogram.common.db.RowValues;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.integration.ContentCatalogService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 扫码解锁（付费层）：盲盒内唯一二维码，全局一次性使用。
 * 核销成功后返回该角色的完整资料（故事文本、纹样、祝福语等）。
 * 音频与 AI 动画依赖素材生产，暂不随核销返回。
 */
@Service
public class UnlockService implements ApplicationRunner {

    private static final List<String[]> DEMO_CODES = List.of(
        new String[] {"PAPER-FROG-2026-0001", "forest"},
        new String[] {"PAPER-FROG-2026-0002", "hibernation"},
        new String[] {"PAPER-FROG-2026-0003", "ginseng"});

    private final UnlockCodeMapper mapper;
    private final ContentCatalogService content;

    public UnlockService(UnlockCodeMapper mapper, ContentCatalogService content) {
        this.mapper = mapper;
        this.content = content;
    }

    /** 启动后播种演示码（is_test=1），此时 content_frog 已由内容目录服务导入。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String[] demo : DEMO_CODES) {
            mapper.insertCodeIfAbsent(demo[0], demo[1], true);
        }
    }

    @Transactional
    public Map<String, Object> redeem(String userId, String code) {
        Map<String, Object> codeRow = mapper.selectByCodeForUpdate(code);
        if (codeRow == null) throw new BusinessException(ErrorCode.NOT_FOUND, "二维码无效");
        if (!"unused".equals(String.valueOf(RowValues.valueOf(codeRow, "status")))) {
            throw new BusinessException(ErrorCode.CONFLICT, "该二维码已被使用");
        }
        String frogId = String.valueOf(RowValues.valueOf(codeRow, "frogId"));

        if (mapper.markRedeemed(((Number) RowValues.valueOf(codeRow, "id")).longValue(), userId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该二维码已被使用");
        }
        mapper.insertRecord(Long.parseLong(userId), code);

        Map<String, Object> frog = content.frog(frogId);
        if (frog == null) throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", true);
        response.put("code", code);
        response.put("characterId", String.valueOf(frog.get("id")));
        response.put("characterName", String.valueOf(frog.get("name")));
        response.put("message", "二维码核验成功，角色故事已解锁");
        response.put("redeemedAt", Instant.now());
        response.put("character", frog);
        return response;
    }

}
