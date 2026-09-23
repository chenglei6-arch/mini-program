package com.chenglei.miniprogram.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * v2 SDK 的 OSSClient 用 builder 组装：region 必填，endpoint 可选（不填由 region 推导），
 * 客户端实现了 AutoCloseable，由容器负责 close。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "close")
    OSSClient ossClient(OssProperties properties) {
        String region = properties.resolvedRegion();
        if (region == null || region.isBlank()) {
            throw new IllegalStateException(
                "app.oss.region 未配置且无法从 endpoint 推导，例如 endpoint=oss-cn-beijing.aliyuncs.com 对应 region=cn-beijing");
        }

        OSSClientBuilder builder = OSSClient.newBuilder()
            .credentialsProvider(credentialsProvider(properties))
            .region(region);

        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpoint(properties.endpoint());
        }
        return builder.build();
    }

    private CredentialsProvider credentialsProvider(OssProperties properties) {
        if (properties.hasStaticCredentials()) {
            return new StaticCredentialsProvider(properties.accessKeyId(), properties.accessKeySecret());
        }
        return new EnvironmentVariableCredentialsProvider();
    }
}
