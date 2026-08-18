# Mini Program Backend

建行杯小程序后端基础工程，使用 Java 21、Spring Boot 3.4、MyBatis-Plus、MySQL、Redis 和 Flyway。

## 本地启动

1. 将 `.env.example` 复制为 `.env` 并修改本地密码。
2. 安装 Docker Desktop 后执行 `docker compose up -d mysql redis`。
3. 执行 `./mvnw spring-boot:run`，Windows 使用 `mvnw.cmd spring-boot:run`。
4. 访问 `http://localhost:8080/v1/system/ping` 验证服务。

Compose 默认把 MySQL 映射到本机 `3307`，避免与已安装的本机 MySQL `3306` 冲突。

接口文档位于 `http://localhost:8080/swagger-ui.html`，健康检查位于 `http://localhost:8080/actuator/health`。

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
