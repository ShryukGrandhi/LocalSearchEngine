#!/usr/bin/env bash
# Builds the app, compiles /test and runs every JUnit 5 test.
# The JUnit console launcher is downloaded once into out/lib.
set -euo pipefail
cd "$(dirname "$0")/.."

JUNIT_VERSION=1.10.2
JUNIT_JAR="out/lib/junit-platform-console-standalone-${JUNIT_VERSION}.jar"
JUNIT_URL="https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/junit-platform-console-standalone-${JUNIT_VERSION}.jar"

# Java on Windows (Git Bash/MSYS/Cygwin) expects ';' between classpath entries.
SEP=':'
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; esac

bash build/build.sh

if [ ! -f "$JUNIT_JAR" ]; then
  mkdir -p out/lib
  echo "Downloading JUnit ${JUNIT_VERSION}..."
  curl -fsSL -o "$JUNIT_JAR" "$JUNIT_URL"
fi

rm -rf out/test-classes
mkdir -p out/test-classes
find test -name '*.java' > out/test-sources.txt
javac --release 17 -encoding UTF-8 -d out/test-classes -cp "out/classes${SEP}${JUNIT_JAR}" @out/test-sources.txt

java -Djava.awt.headless=true -jar "$JUNIT_JAR" execute \
  --disable-banner \
  --class-path "out/test-classes${SEP}out/classes" \
  --scan-class-path \
  --details=tree
