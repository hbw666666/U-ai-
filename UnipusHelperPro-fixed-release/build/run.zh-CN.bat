@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem  UnipusHelperPro 启动器（已修复版）
rem
rem  修复内容：
rem   1. 重复启动检测：程序没在跑才启动。以前双击两次会出现两个实例，
rem      两个 JVM 抢同一个 logs\latest.log，后启动的那个报
rem      “另一个程序正在使用此文件，进程无法访问”。
rem   2. 本次运行的日志单独命名（-Duhp.logfile），实例之间不共用日志文件。
rem   3. 不再把 java 的输出丢进 nul，出错时能看到原因。
rem   4. Java 查找交给 find-java.ps1（注册表 + 实测版本），
rem      不再在批处理里解析 java -version —— 原版那种写法在本机取不到版本号，
rem      会退化成反复要求输入序号。
rem   5. 路径统一换算成 8.3 短路径：目录名里的括号（如“新建文件夹 (3)”）
rem      会让 cmd 在 if 条件里直接报「此时不应有 ...」。
rem
rem  用法：双击运行（后台启动）  或  run.bat --foreground（前台运行，可看输出）
rem ============================================================

set "JAR_NAME=UnipusHelperPro-1.0.3.jar"
set "SCRIPT_DIR=%~dp0"
set "BACKGROUND=1"
set "SELECTED_JAVA="

rem ---------- 0. 换成 8.3 短路径（绕开目录名里的括号） ----------
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

rem ---------- 1. 重复启动检测 ----------
call :is_running
if defined RUNNING_PID goto :already_running

rem ---------- 2. 本次运行独立的日志文件 ----------
set "STAMP=start"
for /f "usebackq delims=" %%t in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss" 2^>nul`) do set "STAMP=%%t"
set "LOGOPT=-Duhp.logfile=logs/latest-%STAMP%.log"

rem ---------- 3. 找 Java 25+ ----------
if exist "%SCRIPT_DIR%find-java.ps1" (
  for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%find-java.ps1" 2^>nul`) do set "SELECTED_JAVA=%%j"
)
if not defined SELECTED_JAVA goto :no_java

rem ---------- 4. 启动 ----------
if "%BACKGROUND%"=="1" (
  start "" /min "%SELECTED_JAVA%" %LOGOPT% -jar "%JAR_PATH%"
  echo.
  echo 已启动 UnipusHelperPro   ^(Java: %SELECTED_JAVA%^)
  echo 日志文件: %SCRIPT_DIR%logs\latest-%STAMP%.log
  exit /b 0
)

echo.
echo 使用 %SELECTED_JAVA% 前台运行，日志同样写入 logs\latest-%STAMP%.log
echo 关闭本窗口即结束程序。
echo.
"%SELECTED_JAVA%" %LOGOPT% -jar "%JAR_PATH%"
set "EXITCODE=%ERRORLEVEL%"
echo.
echo 程序已退出，退出码 %EXITCODE%。
pause
exit /b %EXITCODE%

rem ============================================================
rem  分支与子过程
rem ============================================================

:no_jar
echo 未找到文件: %JAR_PATH%
echo 请把 run.bat 放在与 %JAR_NAME% 相同的目录下运行。
pause
exit /b 1

:already_running
echo ============================================================
echo  UnipusHelperPro 已经在运行中  ^(PID !RUNNING_PID!^)
echo ============================================================
echo.
echo  重复启动会让两个进程抢同一个日志文件，并同时操作同一个账号，
echo  所以这次不再启动第二个实例。
echo.
echo  如果找不到已经打开的窗口，请先在任务管理器里结束 java.exe，
echo  再重新双击本文件。
echo.
pause
exit /b 0

:no_java
echo.
echo 未找到 Java 25 或更高版本，程序无法启动。
echo.
echo 你可以：
echo   1^) 安装 Java 25+ ^(https://adoptium.net^)
echo   2^) 或把 JAVA_HOME 指向已有的 Java 25+ 目录后重试
echo.
pause
exit /b 1

rem ------------------------------------------------------------
rem  找出「命令行里带本 jar 名字」的 java 进程。
rem  这里只匹配 jar 文件名，不用完整路径 —— 完整路径里的括号
rem  会让 for /f 的命令解析直接失败。
rem ------------------------------------------------------------
:is_running
set "RUNNING_PID="
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$n='%JAR_NAME%';$p=Get-CimInstance Win32_Process -Filter 'Name=''java.exe''' | Where-Object { $_.CommandLine -like $('*'+$n+'*') } | Select-Object -First 1; $p.ProcessId" 2^>nul`) do set "RUNNING_PID=%%p"
exit /b 0
