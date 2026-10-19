#!/usr/bin/env bash
# Builds and starts the application. Pass -Dlocalsearch.home=<dir> via JAVA_OPTS to use another library folder.
set -euo pipefail
cd "$(dirname "$0")/.."

bash build/build.sh
exec java ${JAVA_OPTS:-} -jar out/local-search-engine.jar
