#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -d out ]; then ./build.sh; fi
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA="java"; fi
exec $JAVA -cp out server.HttpServerMain
