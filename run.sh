#!/usr/bin/env bash
# 植物园游线规划器：编译并启动（仅需 JDK 17+，无第三方依赖）
set -euo pipefail
cd "$(dirname "$0")"

# 若系统没有 java，尝试使用本仓库同级目录下载的 JDK
if ! command -v java >/dev/null 2>&1; then
  for d in "$HOME"/tools/jdk-*; do
    if [ -x "$d/bin/java" ]; then export JAVA_HOME="$d"; export PATH="$JAVA_HOME/bin:$PATH"; break; fi
  done
fi

echo "==> 使用 Java：$(java -version 2>&1 | head -1)"
echo "==> 编译中…"
mkdir -p build/classes
find src/main/java -name '*.java' > build/sources.txt
javac -encoding UTF-8 -d build/classes @build/sources.txt

PORT="${PORT:-8080}"
echo "==> 启动服务：http://localhost:${PORT}  （管理口令默认 admin123，可用 ADMIN_TOKEN 环境变量覆盖）"
exec java -cp build/classes com.botanic.garden.Main
