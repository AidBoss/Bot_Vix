#!/usr/bin/env bash
# Chạy bot ở local: tự nạp .env, build nếu cần, rồi khởi động.
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "❌ Chưa có file .env. Copy .env.example thành .env rồi điền token/key."
  exit 1
fi

# Nạp biến từ .env vào môi trường
set -a
# shellcheck disable=SC1091
source .env
set +a

# Build nếu chưa có jar hoặc khi truyền tham số "build"
if [[ ! -f target/bot.jar || "${1:-}" == "build" ]]; then
  echo "🔨 Đang build..."
  mvn -q clean package -DskipTests
fi

# Chọn Java >= 21 để chạy (jar build bằng Java 21). Ưu tiên JDK Homebrew nếu java
# mặc định trên PATH quá cũ.
JAVA_BIN="java"
if [[ -x /opt/homebrew/opt/openjdk/bin/java ]]; then
  JAVA_BIN="/opt/homebrew/opt/openjdk/bin/java"
fi

echo "🚀 Khởi động bot... ($("$JAVA_BIN" -version 2>&1 | head -1))"
exec "$JAVA_BIN" -jar target/bot.jar
