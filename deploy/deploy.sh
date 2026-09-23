#!/usr/bin/env bash
# 在目标 Linux 服务器上以 root 执行的一键部署脚本。
# 依赖同目录下的 app.jar、.env、mini_program_dump.sql、mini-program-backend.service。
#
#   sudo ./deploy.sh              # 首次部署：建库、导入数据、装服务
#   sudo ./deploy.sh --skip-db    # 不动数据库（库已存在，或想让 Flyway 自己建表）
#   sudo ./deploy.sh --reset-db   # 销毁并重建库后重新导入（会丢数据）
#
# MySQL 管理员认证：默认用 `mysql -uroot`（Debian/Ubuntu 的 socket 认证）。
# 若 root 需要密码，改为 `MYSQL_ROOT_PASSWORD=xxx sudo -E ./deploy.sh`。
set -euo pipefail

APP_DIR=/opt/mini-program-backend
APP_USER=appuser
SERVICE_NAME=mini-program-backend
BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SKIP_DB=0
RESET_DB=0
for arg in "$@"; do
    case "$arg" in
        --skip-db) SKIP_DB=1 ;;
        --reset-db) RESET_DB=1 ;;
        -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
        *) echo "未知参数：$arg（-h 查看用法）" >&2; exit 2 ;;
    esac
done

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m错误：%s\033[0m\n' "$*" >&2; exit 1; }

# .env 里 DB_URL 含 & 号，不能用 source 读取，逐行取值
env_get() { grep -m1 "^$1=" "$BUNDLE_DIR/.env" | cut -d= -f2- || true; }

[[ $EUID -eq 0 ]] || die "请用 root 运行：sudo ./deploy.sh"

for f in app.jar .env mini-program-backend.service; do
    [[ -f "$BUNDLE_DIR/$f" ]] || die "缺少文件 $f，请确认在完整部署包目录内执行"
done
[[ $SKIP_DB -eq 1 || -f "$BUNDLE_DIR/mini_program_dump.sql" ]] || \
    die "缺少 mini_program_dump.sql（或加 --skip-db 跳过数据库初始化）"

DB_NAME="$(env_get DB_NAME)";           DB_NAME="${DB_NAME:-mini_program}"
DB_USERNAME="$(env_get DB_USERNAME)";   DB_USERNAME="${DB_USERNAME:-mini_program_app}"
DB_PASSWORD="$(env_get DB_PASSWORD)"
[[ -n "$DB_PASSWORD" ]] || die ".env 中 DB_PASSWORD 为空"
[[ "$DB_PASSWORD" != *"'"* ]] || die "DB_PASSWORD 不能包含单引号"

log "检查 Java 21"
if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -qE '"(21|22|23|24)'; then
    java -version 2>&1 | head -1
else
    die "未检测到 JDK 21+。Ubuntu/Debian: apt install -y openjdk-21-jre-headless；RHEL/CentOS/Rocky: dnf install -y java-21-openjdk-headless"
fi

if [[ $SKIP_DB -eq 0 ]]; then
    log "检查 MySQL"
    command -v mysql >/dev/null 2>&1 || die "未安装 mysql 客户端。Ubuntu/Debian: apt install -y mysql-server；RHEL 系: dnf install -y mysql-server"
    mysql --version | head -1
    MYSQL_VERSION="$(mysql --version | grep -oE '[0-9]+\.[0-9]+' | head -1)"
    awk -v v="$MYSQL_VERSION" 'BEGIN{ if (v+0 < 8.0) exit 1 }' || \
        die "MySQL 版本 $MYSQL_VERSION 过低，库使用 utf8mb4_0900_ai_ci 排序规则，需要 8.0+（建议 8.4 LTS）"

    if [[ -n "${MYSQL_ROOT_PASSWORD:-}" ]]; then
        mysql_admin() { mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"; }
    else
        mysql_admin() { mysql -uroot "$@"; }
    fi
    mysql_admin -e 'SELECT 1' >/dev/null 2>&1 || \
        die "MySQL root 认证失败。root 有密码时请用：MYSQL_ROOT_PASSWORD=xxx sudo -E ./deploy.sh"

    log "初始化数据库 $DB_NAME"
    if [[ $RESET_DB -eq 1 ]]; then
        echo "  --reset-db：销毁并重建 $DB_NAME"
        mysql_admin -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;"
    fi
    mysql_admin <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USERNAME'@'localhost';
FLUSH PRIVILEGES;
SQL

    TABLE_COUNT="$(mysql_admin -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
    if [[ "$TABLE_COUNT" -eq 0 ]]; then
        echo "  库为空，导入 mini_program_dump.sql"
        mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" < "$BUNDLE_DIR/mini_program_dump.sql"
        echo "  导入完成，表数量：$(mysql_admin -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
    else
        echo "  库中已有 $TABLE_COUNT 张表，跳过导入（需重建请加 --reset-db）"
    fi
fi

log "安装应用到 $APP_DIR"
id -u "$APP_USER" >/dev/null 2>&1 || useradd --system --shell /usr/sbin/nologin --home-dir "$APP_DIR" "$APP_USER"
[[ -d "$APP_DIR" ]] || mkdir -p "$APP_DIR"
install -o "$APP_USER" -g "$APP_USER" -m 0644 "$BUNDLE_DIR/app.jar" "$APP_DIR/app.jar"
install -o "$APP_USER" -g "$APP_USER" -m 0600 "$BUNDLE_DIR/.env" "$APP_DIR/.env"
# 部署包里可能附带了文档，一并放到运行目录便于排查
if [[ -f "$BUNDLE_DIR/README.md" ]]; then
    install -o "$APP_USER" -g "$APP_USER" -m 0644 "$BUNDLE_DIR/README.md" "$APP_DIR/README.md"
fi
chown -R "$APP_USER:$APP_USER" "$APP_DIR"

log "注册 systemd 服务 $SERVICE_NAME"
install -m 0644 "$BUNDLE_DIR/mini-program-backend.service" "/etc/systemd/system/$SERVICE_NAME.service"
systemctl daemon-reload
systemctl enable "$SERVICE_NAME" >/dev/null
systemctl restart "$SERVICE_NAME"

log "等待服务就绪"
PORT="$(env_get SERVER_PORT)"; PORT="${PORT:-8080}"
for i in $(seq 1 30); do
    if curl -fsS -m 3 "http://127.0.0.1:$PORT/v1/system/ping" >/dev/null 2>&1; then
        echo "  服务已就绪：http://127.0.0.1:$PORT/v1/system/ping"
        curl -s "http://127.0.0.1:$PORT/actuator/health"; echo
        break
    fi
    if [[ $i -eq 30 ]]; then
        echo "  30 秒内未就绪，最近日志："
        journalctl -u "$SERVICE_NAME" -n 40 --no-pager
        exit 1
    fi
    sleep 2
done

cat <<EOF

部署完成。常用命令：
  systemctl status $SERVICE_NAME
  systemctl restart $SERVICE_NAME
  journalctl -u $SERVICE_NAME -f

还需手动完成：
  1. 确认 $APP_DIR/.env 中 WECHAT_APPID / WECHAT_SECRET 已填正式小程序凭据
  2. 确认 APP_AUTH_DEV_OPENID 为空、CONTENT_INCLUDE_TEST_DATA=false
  3. 配好反向代理与 HTTPS，并在微信公众平台登记 request 合法域名
EOF
