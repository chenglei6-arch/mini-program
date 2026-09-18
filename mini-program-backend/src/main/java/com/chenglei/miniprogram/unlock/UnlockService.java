package com.chenglei.miniprogram.unlock;

import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.integration.ContentCatalogService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 扫码解锁（付费层）：盲盒内唯一二维码，全局一次性使用。
 * 核销成功后返回该角色的完整资料（故事文本、纹样、祝福语等）。
 * 音频与 AI 动画依赖素材生产，暂不随核销返回。
 */
@Service
public class UnlockService {

    private final UnlockCodeMapper mapper;
    private final ContentCatalogService content;

    public UnlockService(UnlockCodeMapper mapper, ContentCatalogService content) {
        this.mapper = mapper;
        this.content = content;
    }

    @Transactional
    public Map<String, Object> redeem(String userId, String code) {
        Map<String, Object> codeRow = mapper.selectByCodeForUpdate(code);
        if (codeRow == null) throw new BusinessException(ErrorCode.NOT_FOUND, "二维码无效");
        if (!"unused".equals(codeRow.get("status"))) {
            throw new BusinessException(ErrorCode.CONFLICT, "该二维码已被使用");
        }
        String frogId = (String) codeRow.get("frogId");

        if (mapper.markRedeemed(((Number) codeRow.get("id")).longValue(), userId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该二维码已被使用");
        }

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
