package com.chenglei.miniprogram.auth;

import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 微信 code2session 封装。appid/secret 通过环境变量 WECHAT_APPID、WECHAT_SECRET 注入；
 * 两者都配置时走真实登录，否则由 {@link DbSessionService} 使用固定开发账号。
 */
@Service
public class WeChatApiService {

    private static final Logger log = LoggerFactory.getLogger(WeChatApiService.class);
    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    /** 客户端问题：code 无效、过期或已被使用，应引导重新 wx.login。 */
    private static final Set<String> INVALID_CODE_ERRCODES = Set.of("40029", "40163", "40226");

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String appid;
    private final String secret;

    public WeChatApiService(@Value("${app.wechat.appid:}") String appid,
        @Value("${app.wechat.secret:}") String secret) {
        this.appid = appid == null ? "" : appid.trim();
        this.secret = secret == null ? "" : secret.trim();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        if (!isConfigured()) {
            log.warn("未配置 WECHAT_APPID/WECHAT_SECRET，登录将使用固定开发账号（仅供本地联调，切勿用于生产）");
        }
    }

    public boolean isConfigured() {
        return !appid.isEmpty() && !secret.isEmpty();
    }

    public record CodeSession(String openid, String unionid) { }

    public CodeSession exchangeCode(String code) {
        JsonNode body;
        try {
            String raw = restClient.get()
                .uri(JSCODE2SESSION_URL + "?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
                    appid, secret, code)
                .retrieve()
                .body(String.class);
            body = objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("jscode2session 调用异常", e);
            throw new BusinessException(ErrorCode.WECHAT_502, "微信登录接口暂时不可用，请稍后重试");
        }

        String errcode = body.hasNonNull("errcode") ? body.get("errcode").asText() : "0";
        if (!"0".equals(errcode)) {
            log.warn("jscode2session 失败 errcode={} errmsg={}", errcode, body.path("errmsg").asText());
            if (INVALID_CODE_ERRCODES.contains(errcode)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "登录凭据无效或已过期，请重新进入小程序");
            }
            throw new BusinessException(ErrorCode.WECHAT_502, "微信登录接口调用失败，请稍后重试");
        }

        String openid = body.path("openid").asText("");
        if (openid.isBlank()) {
            throw new BusinessException(ErrorCode.WECHAT_502, "微信登录未返回 openid");
        }
        String unionid = body.hasNonNull("unionid") ? body.get("unionid").asText() : null;
        return new CodeSession(openid, unionid);
    }
}
