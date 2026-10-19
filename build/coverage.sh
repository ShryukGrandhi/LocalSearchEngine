#!/usr/bin/env bash
# Runs the tests under the JaCoCo agent and writes an HTML report to out/coverage/index.html.
# Coverage is measured for /models and /controllers; /views is Swing code exercised by hand.
set -euo pipefail
cd "$(dirname "$0")/.."

JACOCO_VERSION=0.8.12
AGENT="out/lib/jacocoagent-${JACOCO_VERSION}.jar"
CLI="out/lib/jacococli-${JACOCO_VERSION}.jar"
BASE="https://repo1.maven.org/maven2/org/jacoco"

bash build/test.sh > /dev/null

if [ ! -f "$AGENT" ]; then
  curl -fsSL -o "$AGENT" "${BASE}/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"
fi
if [ ! -f "$CLI" ]; then
  curl -fsSL -o "$CLI" "${BASE}/org.jacoco.cli/${JACOCO_VERSION}/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar"
fi

SEP=':'
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; esac
JUNIT_JAR="$(ls out/lib/junit-platform-console-standalone-*.jar | head -n 1)"

rm -f out/jacoco.exec
java -Djava.awt.headless=true -javaagent:"$AGENT=destfile=out/jacoco.exec" -jar "$JUNIT_JAR" execute \
  --disable-banner --details=none \
  --class-path "out/test-classes${SEP}out/classes" --scan-class-path

rm -rf out/coverage-classes
mkdir -p out/coverage-classes
cp -r out/classes/models out/classes/controllers out/coverage-classes/
java -jar "$CLI" report out/jacoco.exec \
  --classfiles out/coverage-classes \
  --sourcefiles main \
  --html out/coverage \
  --csv out/coverage.csv
awk -F, 'NR > 1 { missed += $8; covered += $9 } END { printf "Line coverage (models + controllers): %.1f%%\n", 100 * covered / (missed + covered) }' out/coverage.csv
echo "Report: out/coverage/index.html"
