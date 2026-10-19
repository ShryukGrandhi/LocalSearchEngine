@echo off
rem Builds the app, compiles \test and runs every JUnit 5 test (Windows).
setlocal
cd /d "%~dp0.."

set JUNIT_VERSION=1.10.2
set JUNIT_JAR=out\lib\junit-platform-console-standalone-%JUNIT_VERSION%.jar
set JUNIT_URL=https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/%JUNIT_VERSION%/junit-platform-console-standalone-%JUNIT_VERSION%.jar

call build\build.cmd || exit /b 1

if not exist "%JUNIT_JAR%" (
  if not exist out\lib mkdir out\lib
  echo Downloading JUnit %JUNIT_VERSION%...
  curl -fsSL -o "%JUNIT_JAR%" "%JUNIT_URL%" || exit /b 1
)

if exist out\test-classes rmdir /s /q out\test-classes
mkdir out\test-classes
powershell -NoProfile -Command "Get-ChildItem -Recurse test -Filter *.java | ForEach-Object { '\"' + ($_.FullName -replace '\\','/') + '\"' } | Set-Content -Encoding ascii out\test-sources.txt"
javac --release 17 -encoding UTF-8 -d out\test-classes -cp "out\classes;%JUNIT_JAR%" @out\test-sources.txt || exit /b 1

java -Djava.awt.headless=true -jar "%JUNIT_JAR%" execute --disable-banner --class-path "out\test-classes;out\classes" --scan-class-path --details=tree
