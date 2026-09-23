import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.aliyun.sdk.service.oss2.transfermanager.Uploader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 静态资源批量上传工具（阿里云 OSS v2 SDK），需在 mini-program-backend 目录下运行。
 *
 * 用法：java UploadStaticAssets [本地目录 对象键前缀]
 *   默认：src/main/resources/static/assets -> assets/
 *   恢复部件图示例：java UploadStaticAssets src/main/resources/static/assets/frogs/components assets/frogs/components
 *
 * 凭据优先读环境变量，其次当前目录 .env（OSS_ENDPOINT / OSS_REGION / OSS_BUCKET /
 * OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET）。上传对象统一 public-read ACL 与 1 天缓存；
 * 仅图片扩展名会被上传，README 等其他文件自动跳过。
 */
public class UploadStaticAssets {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "svg", "image/svg+xml");

    public static void main(String[] args) throws Exception {
        Path localDir = Path.of(args.length > 0 ? args[0] : "src/main/resources/static/assets");
        String keyPrefix = (args.length > 1 ? args[1] : "assets").replaceAll("^/+|/+$", "");
        if (!Files.isDirectory(localDir)) {
            throw new IllegalStateException("目录不存在：" + localDir.toAbsolutePath());
        }

        Map<String, String> env = loadEnv(Path.of(".env"));
        String endpoint = required(env, "OSS_ENDPOINT");
        String bucket = required(env, "OSS_BUCKET");
        String urlBase = "https://" + bucket + "." + endpoint;

        int ok = 0;
        int failed = 0;
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(new StaticCredentialsProvider(
                        required(env, "OSS_ACCESS_KEY_ID"), required(env, "OSS_ACCESS_KEY_SECRET")))
                .region(required(env, "OSS_REGION"))
                .endpoint(endpoint)
                .build();
             Stream<Path> files = Files.walk(localDir)) {

            Uploader uploader = new Uploader(client);
            List<Path> images = files.filter(Files::isRegularFile)
                    .filter(p -> CONTENT_TYPES.containsKey(ext(p.getFileName().toString())))
                    .sorted()
                    .toList();
            if (images.isEmpty()) {
                System.out.println("目录中没有可上传的图片：" + localDir.toAbsolutePath());
                return;
            }

            for (Path file : images) {
                String key = keyPrefix + "/" + localDir.relativize(file).toString().replace('\\', '/');
                String contentType = CONTENT_TYPES.get(ext(file.getFileName().toString()));
                try (InputStream in = Files.newInputStream(file)) {
                    var result = uploader.uploadFrom(PutObjectRequest.newBuilder()
                                    .bucket(bucket)
                                    .key(key)
                                    .contentType(contentType)
                                    .objectAcl("public-read")
                                    .cacheControl("public, max-age=86400")
                                    .build(),
                            BinaryData.fromStream(in, Files.size(file)));
                    if (result.statusCode() == 200) {
                        ok++;
                        System.out.println("OK   " + urlBase + "/" + key);
                    } else {
                        failed++;
                        System.out.println("FAIL(" + result.statusCode() + ")  " + key);
                    }
                } catch (Exception e) {
                    failed++;
                    System.out.println("FAIL  " + key + " : " + e.getMessage());
                }
            }
            System.out.printf("完成：成功 %d，失败 %d，共 %d 个文件%n", ok, failed, ok + failed);
            if (failed > 0) {
                System.exit(1);
            }
        }
    }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 环境变量优先，其次 .env 文件；与 Spring 的 .env 导入行为保持一致。 */
    private static Map<String, String> loadEnv(Path envFile) throws IOException {
        Map<String, String> env = new HashMap<>();
        if (Files.isRegularFile(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (!line.isEmpty() && !line.startsWith("#") && eq > 0) {
                    env.putIfAbsent(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        }
        for (String name : List.of("OSS_ENDPOINT", "OSS_REGION", "OSS_BUCKET",
                "OSS_ACCESS_KEY_ID", "OSS_ACCESS_KEY_SECRET")) {
            String fromOs = System.getenv(name);
            if (fromOs != null && !fromOs.isBlank()) {
                env.put(name, fromOs);
            }
        }
        return env;
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 " + name + "（环境变量或 .env）");
        }
        return value;
    }
}
