@echo off
rem Builds and starts the application (Windows).
setlocal
cd /d "%~dp0.."

call build\build.cmd || exit /b 1
start "" javaw %JAVA_OPTS% -jar out\local-search-engine.jar
