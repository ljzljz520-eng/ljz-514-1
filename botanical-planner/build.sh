#!/usr/bin/env bash
# 编译后端到 out/ 目录（仅需 JDK 17+，无任何第三方依赖）
set -euo pipefail
cd "$(dirname "$0")"

if [ -n "${JAVA_HOME:-}" ]; then JAVAC="$JAVA_HOME/bin/javac"; else JAVAC="javac"; fi
$JAVAC -version >/dev/null 2>&1 || { echo "未找到 javac，请先安装 JDK 17 或更高版本"; exit 1; }

rm -rf out
mkdir -p out
find src -name "*.java" > sources.txt
$JAVAC -encoding UTF-8 -d out @sources.txt
rm -f sources.txt
echo "构建完成：out/"
