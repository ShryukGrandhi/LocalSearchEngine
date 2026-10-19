#!/usr/bin/env bash
# Compiles /main into out/classes and packages out/local-search-engine.jar.
set -euo pipefail
cd "$(dirname "$0")/.."

rm -rf out/classes
mkdir -p out/classes
find main -name '*.java' > out/sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all,-serial -d out/classes @out/sources.txt
jar --create --file out/local-search-engine.jar --main-class app.Main -C out/classes .
echo "Built out/local-search-engine.jar"
