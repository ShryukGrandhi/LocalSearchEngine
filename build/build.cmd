@echo off
rem Compiles \main into out\classes and packages out\local-search-engine.jar (Windows).
setlocal
cd /d "%~dp0.."

if exist out\classes rmdir /s /q out\classes
mkdir out\classes
powershell -NoProfile -Command "Get-ChildItem -Recurse main -Filter *.java | ForEach-Object { '\"' + ($_.FullName -replace '\\','/') + '\"' } | Set-Content -Encoding ascii out\sources.txt"
javac --release 17 -encoding UTF-8 -Xlint:all,-serial -d out\classes @out\sources.txt || exit /b 1
jar --create --file out\local-search-engine.jar --main-class app.Main -C out\classes . || exit /b 1
echo Built out\local-search-engine.jar
