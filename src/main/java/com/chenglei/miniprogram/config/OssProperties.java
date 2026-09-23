package com.chenglei.miniprogram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.oss 配置项。密钥留空时运行时回退到 v2 默认的环境变量链
 * OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET（见 EnvironmentVariableCredentialsProvider）。
 */
@ConfigurationProperties(prefix = "app.oss")
public record OssProperties(
    String endpoint,
    String region,
    String bucket,
    String accessKeyId,
    String accessKeySecret,
    String publicBaseUrl
) {

    public boolean hasStaticCredentials() {
        return notBlank(accessKeyId) && notBlank(accessKeySecret);
    }

    /** endpoint 形如 oss-cn-beijing.aliyuncs.com 时，region 可推导为 cn-beijing。 */
    public String resolvedRegion() {
        if (notBlank(region)) {
            return region;
        }
        if (notBlank(endpoint) && endpoint.startsWith("oss-")) {
            int dot = endpoint.indexOf('.');
            return endpoint.substring(4, dot);
        }
        return null;
    }

    /** 未显式配置公网地址时，按 v1 惯例的虚拟主机形式推导：https://{bucket}.{endpoint}。 */
    public String resolvedPublicBaseUrl() {
        String base = notBlank(publicBaseUrl) ? publicBaseUrl : "https://" + bucket + "." + endpoint;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
