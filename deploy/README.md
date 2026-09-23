# 后端部署到 Linux 服务器

本目录是部署到自有公网 IP 服务器的完整材料。当前本机（校园网 NAT 后面）通过 Cloudflare 隧道临时对外提供服务的方案，在迁移完成前保持可用。

## 部署包内容

| 文件 | 来源 | 说明 |
| --- | --- | --- |
| `app.jar` | `mvn -B -ntp -DskipTests package` 产物 | Spring Boot 可执行 jar |
| `.env` | 由 `.env.production.example` 复制填写 | 生产环境变量，**不入库** |
| `mini_program_dump.sql` | `deploy/db/` | 库结构与数据快照，**不入库** |
| `deploy.sh` | 本目录 | 一键部署脚本 |
| `mini-program-backend.service` | 本目录 | systemd 服务单元 |
| `nginx-mini-program.conf` | 本目录 | 反向代理示例 |
| `README.md` | 本目录 | 本文件 |

本地打包：

```bash
cd mini-program-backend
mvn -B -ntp -DskipTests package
mkdir -p /tmp/deploy-bundle && cd /tmp/deploy-bundle
cp ../target/mini-program-backend-0.0.1-SNAPSHOT.jar app.jar
cp ../deploy/db/mini_program_dump.sql .
cp ../deploy/deploy.sh ../deploy/mini-program-backend.service \
   ../deploy/nginx-mini-program.conf ../deploy/README.md .
cp ../deploy/.env.production.example .env   # 填好真实值
tar czf ../mini-program-backend-deploy.tar.gz .
```

## 服务器要求

- Linux，root 或 sudo 权限
- **JDK 21+**（`apt install -y openjdk-21-jre-headless` 或 `dnf install -y java-21-openjdk-headless`）
- **MySQL 8.0+，建议 8.4 LTS**。库使用 `utf8mb4_0900_ai_ci` 排序规则，低于 8.0 导入会失败
- 公网可达 80/443（或由 Cloudflare 回源到 8080）
- 内存 1G 以上：`-XX:MaxRAMPercentage=75` 按容器/物理内存比例分配堆

## 部署步骤

```bash
# 1) 上传并解包
scp mini-program-backend-deploy.tar.gz root@<服务器IP>:/root/
ssh root@<服务器IP>
mkdir -p /root/deploy && tar xzf /root/mini-program-backend-deploy.tar.gz -C /root/deploy && cd /root/deploy

# 2) 二次确认 .env（尤其是 DB_PASSWORD、WECHAT_APPID/SECRET）
vi .env

# 3) 执行部署：建库建账号、导入数据、安装并启动 systemd 服务
chmod +x deploy.sh
./deploy.sh
# root 有 MySQL 密码时：MYSQL_ROOT_PASSWORD=xxx ./deploy.sh
# 库已存在不想覆盖：  ./deploy.sh --skip-db
```

脚本会自动校验 Java/MySQL 版本、建库导数据、安装到 `/opt/mini-program-backend`、注册并拉起 `mini-program-backend` 服务，最后请求 `/v1/system/ping` 和 `/actuator/health` 确认就绪。

数据初始化说明：库为空时导入 `mini_program_dump.sql`（保留本机现有内容和数据）；加 `--skip-db` 则不碰数据库，首次启动由 Flyway 按 `V1..V18` 迁移建表并写入种子内容。

## 接域名

沿用 `www.luolikongchenglei.asia`，小程序端 `config/env.js` 的 `baseUrl` 一行都不用改。

1. 安装 Nginx，把 `nginx-mini-program.conf` 放到 `/etc/nginx/conf.d/`，证书放 `/etc/nginx/certs/`，`nginx -t && systemctl reload nginx`。
2. Cloudflare 侧把 `www` 的解析从 Tunnel 改为 **A 记录指向服务器公网 IP**，SSL/TLS 模式设为 **Full (strict)**（证书用 Cloudflare Origin Certificate，15 年有效期）。
3. 本机隧道在域名切换后即可停掉：`Stop-Process -Name cloudflared`。
4. 若要保留本机做联调环境，另建一个子域名（如 `dev.luolikongchenglei.asia`）继续走隧道，不要和正式域名抢解析。

## 微信侧配置

在微信公众平台「开发管理 → 开发设置 → 服务器域名」把 `https://www.luolikongchenglei.asia` 加入 **request 合法域名**（上传/下载域名按需另加）。

域名要求 HTTPS 且证书有效；服务器在中国大陆境内时，域名需完成 ICP 备案，否则微信不会放行。用 Cloudflare 回源（节点在境外）时通常不涉及境内备案，但请按实际接入方式确认。

## 上线前检查

- [ ] `.env` 中 `WECHAT_APPID` / `WECHAT_SECRET` 已填正式小程序凭据。**未填时任何 `code` 都会换取同一个开发账号的会话**（见 `auth/SessionService.java`），用户数据无法隔离
- [ ] `APP_AUTH_DEV_OPENID` 留空
- [ ] `CONTENT_INCLUDE_TEST_DATA=false`
- [ ] DB 中正式业务数据 `is_test=0`。注意 `unlock_code` 目前 3 条全是 `is_test=1`，正式接口下扫码解锁取不到任何码，上线前需补正式核销码
- [ ] `/v1/system/ping`、`/actuator/health` 经域名访问均 200
- [ ] 带 token 调用 `/v1/content/frogs`、`/v1/home/summary` 返回数据（确认库连的是新服务器而不是本机）

## 回滚

```bash
systemctl stop mini-program-backend        # 停新服务
# Cloudflare 解析改回 Tunnel，本机重新执行：
#   Start-Process 'C:\Program Files (x86)\cloudflared\cloudflared.exe' -ArgumentList 'tunnel','run' -WindowStyle Hidden
```

数据库有改动时，用 `--reset-db` 重新导入 `mini_program_dump.sql` 可恢复到导出时刻的状态。
