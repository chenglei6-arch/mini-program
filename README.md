# 纸韵蛙鸣·哈什蚂传奇（建行杯小程序）

本分支为**前后端一体的可用版本**，两个子项目各自独立、互不依赖构建：

| 目录 | 内容 | 技术栈 |
| --- | --- | --- |
| `mini-program-frontend/` | 微信小程序 | 原生小程序（JS / WXML / WXSS） |
| `mini-program-backend/` | 服务端 | Java 21、Spring Boot 3.4、MySQL、Flyway |

## 怎么跑

**小程序**：用微信开发者工具打开 `mini-program-frontend/` 目录（`project.config.json` 在该目录下）。接口地址在 `mini-program-frontend/config/env.js` 配置。

**后端**：先看 `mini-program-backend/README.md`，在 `mini-program-backend/` 目录下执行：

```bash
docker compose up -d mysql
./mvnw spring-boot:run        # Windows 用 mvnw.cmd spring-boot:run
```

接口文档 `http://localhost:8080/swagger-ui.html`，健康检查 `/actuator/health`。

## 分支说明

- `master`：前后端一体的可用版本（本分支），子目录布局。
- `frontend` / `backend`：两个子项目的独立开发分支，代码位于各自仓库根目录，**与本分支的子目录布局不同**；
  从这两个分支继续往 `master` 合并时需要注意路径差异。

## 素材与设计源文件

- 九只林蛙的运行时 PNG 不入库（见 `mini-program-backend/.gitignore`），部署时需单独投放到服务器目录或 OSS/CDN。
- 设计源文件（PSB/PSD、部件 PNG、设计说明、交付压缩包，约 662MB）归档在仓库外的 `design-sources/`，不参与构建与发布。
