@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem  UnipusHelperPro launcher (fixed version)
rem
rem  Fixes in this launcher:
rem   1. Duplicate-launch detection: refuses to start a second
rem      instance while one is already running. Two instances used
rem      to fight over the same logs\latest.log, producing
rem      "Unable to delete file ... another program is using this file".
rem   2. Each launch writes its own log file (-Duhp.logfile=...), so
rem      instances never share one log file.
rem   3. java output is no longer thrown away, so errors stay visible.
rem   4. Java discovery is done by find-java.ps1 (registry + real
rem      version probe) instead of parsing "java -version" in batch.
rem   5. Paths are converted to 8.3 short form, because a "(...)"
rem      inside a directory name breaks cmd's IF parsing.
rem
rem  Usage: double-click to run in background,
rem         or: run.bat --foreground  (keeps a console, shows output)
rem ============================================================

set "JAR_NAME=UnipusHelperPro-1.0.3.jar"
set "SCRIPT_DIR=%~dp0"
set "BACKGROUND=1"
set "SELECTED_JAVA="

rem ---------- 0. convert to 8.3 short path ----------
set "SHORT_PATH="
for /f "usebackq delims=" %%s in (`powershell -NoProfile -Command "$f=New-Object -ComObject Scripting.FileSystemObject; try { $f.GetFolder('%SCRIPT_DIR:~0,-1%').ShortPath } catch { '' }" 2^>nul`) do set "SHORT_PATH=%%s"
if defined SHORT_PATH (
  if exist "!SHORT_PATH!\*" set "SCRIPT_DIR=!SHORT_PATH!\"
)
set "JAR_PATH=%SCRIPT_DIR%%JAR_NAME%"

if not exist "%JAR_PATH%" goto :no_jar

if "%~1"=="--foreground" (
  set "BACKGROUND=0"
  shift
)

rem ---------- 1. already running? ----------
call :is_running
if defined RUNNING_PID goto :already_running

rem ---------- 2. per-launch log file ----------
set "STAMP=start"
for /f "usebackq delims=" %%t in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss" 2^>nul`) do set "STAMP=%%t"
set "LOGOPT=-Duhp.logfile=logs/latest-%STAMP%.log"

rem ---------- 3. locate Java 25+ ----------
if exist "%SCRIPT_DIR%find-java.ps1" (
  for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%find-java.ps1" 2^>nul`) do set "SELECTED_JAVA=%%j"
)
if not defined SELECTED_JAVA goto :no_java

rem ---------- 4. start ----------
if "%BACKGROUND%"=="1" (
  start "" /min "%SELECTED_JAVA%" %LOGOPT% -jar "%JAR_PATH%"
  echo.
  echo UnipusHelperPro started.  ^(Java: %SELECTED_JAVA%^)
  echo Log file: %SCRIPT_DIR%logs\latest-%STAMP%.log
  exit /b 0
)

echo.
echo Running in foreground with %SELECTED_JAVA%
echo Log file: logs\latest-%STAMP%.log
echo Close this window to stop the program.
echo.
"%SELECTED_JAVA%" %LOGOPT% -jar "%JAR_PATH%"
set "EXITCODE=%ERRORLEVEL%"
echo.
echo Program exited with code %EXITCODE%.
pause
exit /b %EXITCODE%

rem ============================================================
rem  branches and helpers
rem ============================================================

:no_jar
echo File not found: %JAR_PATH%
echo Put run.bat next to %JAR_NAME%.
pause
exit /b 1

:already_running
echo ============================================================
echo  UnipusHelperPro is already running  ^(PID !RUNNING_PID!^)
echo ============================================================
echo.
echo  Starting a second instance would make two processes fight
echo  over the same log file and operate the same account, so this
echo  launch was refused.
echo.
echo  Switch to the window that is already open. If you cannot find
echo  it, end all java.exe processes in Task Manager and start again.
echo.
pause
exit /b 0

:no_java
echo.
echo Java 25 or newer was not found, the program cannot start.
echo.
echo You can:
echo   1^) install Java 25+ ^(https://adoptium.net^)
echo   2^) or point JAVA_HOME at an existing Java 25+ folder
echo.
pause
exit /b 1

rem ------------------------------------------------------------
rem  Match only the jar file name, never the full path: a "(...)"
rem  in the path would break the FOR /F command parsing.
rem ------------------------------------------------------------
:is_running
set "RUNNING_PID="
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$n='%JAR_NAME%';$p=Get-CimInstance Win32_Process -Filter 'Name=''java.exe''' | Where-Object { $_.CommandLine -like $('*'+$n+'*') } | Select-Object -First 1; $p.ProcessId" 2^>nul`) do set "RUNNING_PID=%%p"
exit /b 0
