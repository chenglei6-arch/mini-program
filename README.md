# Mini Program Backend

建行杯小程序后端基础工程，使用 Java 21、Spring Boot 3.4、MyBatis-Plus、MySQL、Redis 和 Flyway。

## 本地启动

1. 将 `.env.example` 复制为 `.env` 并修改本地密码。
2. 安装 Docker Desktop 后执行 `docker compose up -d mysql redis`。
3. 执行 `./mvnw spring-boot:run`，Windows 使用 `mvnw.cmd spring-boot:run`。
4. 访问 `http://localhost:8080/v1/system/ping` 验证服务。

Compose 默认把 MySQL 映射到本机 `3307`，避免与已安装的本机 MySQL `3306` 冲突。

接口文档位于 `http://localhost:8080/swagger-ui.html`，健康检查位于 `http://localhost:8080/actuator/health`。

## 测试数据开关

开发环境默认读取数据库中 `is_test=1` 的测试数据，便于前后端联调。配置项为 `CONTENT_INCLUDE_TEST_DATA`：

- `true`（默认）：林蛙、游戏、纹样、徽章和蛙友动态接口包含测试数据。
- `false`：接口只返回 `is_test=0` 的正式数据，正式部署时应在环境变量中设置为 `false`。

`is_test` 字段应长期保留，用于数据隔离和灰度验证，不需要在上线时删除。测试数据可以在上线前归档或删除，但关闭开关即可保证正式接口不返回它们。首页配置和公益汇总属于单行正式配置，测试记录会保留在数据库中但不会覆盖正式展示。

> **正式上线前必查：**确认所有正式业务数据的 `is_test=0`，并将生产环境变量 `CONTENT_INCLUDE_TEST_DATA` 设置为 `false`，避免测试数据出现在正式接口中。

## 林蛙素材与 OSS

九只林蛙的运行时 PNG 和 DOCX 源文件位于 `src/main/resources/static/assets`，清单接口为 `GET /v1/content/frogs`。蛙资料、章节正文、图片 URL 和 DOCX `sourceUrl` 均由数据库内容表返回；本地默认 URL 分别为 `/assets/frogs/{id}.png` 和 `/assets/sources/frogs/{id}.docx`。

部署到 OSS/CDN 时，将数据库中的 `asset_url` 和 `source_url` 更新为 OSS/CDN URL，例如 `https://cdn.example.com/frogs/forest.png`。业务数据不保存本机绝对路径；迁移存储只需要上传文件并更新 URL，不需要改小程序代码。

如需连同后端一起容器化启动，执行 `docker compose --profile app up -d --build`。

## 项目结构

```text
src/main/java/com/chenglei/miniprogram/
├── MiniProgramBackendApplication.java   # Spring Boot 启动类
├── auth/                                # 登录和开发会话
│   ├── AuthController.java              # 登录接口
│   ├── DevSessionService.java           # 本地开发会话
│   └── DevBearerAuthenticationFilter.java
├── integration/                         # 前后端联调接口
│   └── IntegrationController.java       # 首页、资料、游戏等接口
├── system/                              # 系统接口
│   └── SystemController.java            # ping 接口
├── config/                              # Spring Security 和 Web 配置
├── common/                              # 通用响应、异常和请求处理
└── resources/
    ├── application.yml                  # 默认配置
    ├── application-integration.yml      # 无数据库联调配置
    └── db/migration/                    # Flyway 数据库迁移脚本

src/test/
├── java/com/chenglei/miniprogram/       # 后端集成测试
└── resources/application.yml            # H2 测试数据库配置
```

项目按业务模块划分 Controller，因此没有单独的 `controller` 文件夹。当前 Controller 位于 `auth`、`integration` 和 `system` 包中。

## 数据库

数据库固定为 `mini_program`，应用使用 `mini_program_app` 专用账户。表结构只通过 `src/main/resources/db/migration` 中的 Flyway 脚本变更，业务服务不得使用 MySQL `root` 账户。

`.env`、证书和密钥均被 Git 忽略，生产环境必须通过部署平台注入，不要写入仓库。
