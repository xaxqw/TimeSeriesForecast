@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo ============================================
echo   TimeSeriesForecast - One-click Start
echo   Requires JDK 17+. Uses java -jar (fast path).
echo ============================================
echo.

REM --- Check Java version ---
set "JVER="
for /f tokens^=3 %%v in ('java -version 2^>^&1') do (
  set "JVER=%%~v"
  goto :gotver
)
:gotver
if "%JVER%"=="" (
  echo [ERROR] Java not found. Please install JDK 17+ and add it to PATH.
  pause
  exit /b 1
)
echo Detected Java version: %JVER%
echo %JVER% | findstr /b "1\." >nul 2>nul
if %errorlevel%==0 (
  echo [ERROR] JDK version too old: %JVER%. This project requires JDK 17 or newer.
  echo   Please install JDK 17+ and ensure its bin directory is first in PATH.
  pause
  exit /b 1
)

REM --- Ensure executable jar exists (built once, reused afterwards) ---
REM NOTE: if you change Java source code, delete the target\ folder (or run
REM       mvnw.cmd package -DskipTests) so the next start picks up new code.
set "JAR="
for %%f in (target\time-series-forecast*.jar) do set "JAR=%%f"

if exist "%JAR%" (
  echo Found existing build: %JAR%
) else (
  echo Building project [first run or target missing]... this may take 1-3 min.
  call mvnw.cmd package -DskipTests
  set "JAR="
  for %%f in (target\time-series-forecast*.jar) do set "JAR=%%f"
  if not exist "%JAR%" (
    echo [ERROR] Build failed. Check the Maven log above.
    pause
    exit /b 1
  )
)
REM --- Start server in a new window via java -jar (no Maven overhead) ---
echo Starting TimeSeriesForecast server in a new window...
start "TimeSeriesForecast-Server" cmd /k "java -jar %JAR%"

REM --- Poll /api/health until ready, then open browser ---
echo Waiting for server to be ready (up to ~3 minutes)...
set "URL=http://localhost:8080"

REM Prefer curl for fast polling; fall back to powershell if absent.
where curl.exe >nul 2>nul
if %errorlevel%==0 (set USE_CURL=1) else (set USE_CURL=0)

set READY=0
for /L %%i in (1,1,90) do (
  %SystemRoot%\System32\timeout.exe /t 2 >nul
  if "!USE_CURL!"=="1" (
    for /f %%c in ('curl.exe -s --max-time 2 -o nul -w "%%{http_code}" %URL%/api/health 2^>nul') do set "CODE=%%c"
  ) else (
    for /f %%c in ('powershell -NoProfile -Command "try { (Invoke-WebRequest -Uri '%URL%/api/health' -UseBasicParsing -TimeoutSec 2).StatusCode } catch { 0 }"') do set "CODE=%%c"
  )
  if "!CODE!"=="200" (
    set READY=1
    goto :open
  )
)
:open
if %READY%==1 (
  echo Server is ready. Opening browser...
  start "" "%URL%"
) else (
  echo Server is taking too long or failed. Open %URL% manually and check the server window log.
)

endlocal
