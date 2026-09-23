package com.chenglei.miniprogram.file;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.aliyun.sdk.service.oss2.transfermanager.UploadResult;
import com.aliyun.sdk.service.oss2.transfermanager.Uploader;
import com.chenglei.miniprogram.config.OssProperties;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 参照官方 v2 示例 UploadFile 的写法：Uploader 传输管理器按文件大小自动选择
 * 简单上传或分片上传，并自带重试。每次上传新建 Uploader，避免共享状态。
 */
@Service
public class    OssStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private final OSSClient client;
    private final OssProperties properties;

    public OssStorageService(OSSClient client, OssProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public UploadedFile upload(MultipartFile file) {
        String key = buildKey(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
            ? "application/octet-stream"
            : file.getContentType();

        PutObjectRequest request = PutObjectRequest.newBuilder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .build();

        UploadResult result;
        try (InputStream input = file.getInputStream()) {
            Uploader uploader = new Uploader(client);
            result = uploader.uploadFrom(request, BinaryData.fromStream(input, file.getSize()));
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        } catch (Exception e) {
            log.error("OSS 上传失败 key={}", key, e);
            throw new IllegalStateException("文件上传到对象存储失败", e);
        }

        log.info("OSS 上传成功 key={} etag={} crc64={}", key, result.etag(), result.hashCrc64ecma());
        return new UploadedFile(
            properties.resolvedPublicBaseUrl() + "/" + key,
            key,
            result.etag(),
            file.getSize());
    }

    /** 将 /assets/... 相对路径合成为公网完整 URL；空值与已是绝对 URL 的原样返回。 */
    public String publicUrl(String path) {
        if (path == null || path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = properties.resolvedPublicBaseUrl();
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    /** 对象键不保留用户原始文件名，只保留校验过的扩展名，形如 uploads/2026/09/21/<uuid>.png。 */
    private String buildKey(String originalFilename) {
        String extension = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String candidate = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (candidate.matches("[a-z0-9]{1,10}")) {
                    extension = "." + candidate;
                }
            }
        }
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return "uploads/" + date + "/" + UUID.randomUUID() + extension;
    }
}
