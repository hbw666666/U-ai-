@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem  Rebuild the two download packages from the repository files.
rem  Run this from the repository root (where this script lives).
rem
rem  Produces:
rem    UnipusHelperPro-fixed-1.0.3.zip       program + lib + docs
rem    UnipusHelperPro-fixed-1.0.3-src.zip   patch sources only
rem
rem  Note: the program package needs lib/ (the 16 third-party jars).
rem  The repository does not track lib/, so put it next to this
rem  script before running, or the package will be missing it.
rem ============================================================

set "ROOT=%~dp0"
rem convert to 8.3 short path: "(...)" in a folder name breaks cmd IF parsing
for /f "usebackq delims=" %%s in (`powershell -NoProfile -Command "$f=New-Object -ComObject Scripting.FileSystemObject; try { $f.GetFolder('%ROOT:~0,-1%').ShortPath } catch { '' }" 2^>nul`) do set "SHORT=%%s"
if defined SHORT if exist "!SHORT!\*" set "ROOT=!SHORT!\"

set "PKG=UnipusHelperPro-fixed-1.0.3"
set "STAGE=%ROOT%_build_zip"
set "OUT=%ROOT%%PKG%.zip"
set "OUTSRC=%ROOT%%PKG%-src.zip"
set "PWSH=powershell -NoProfile -ExecutionPolicy Bypass -Command"

if not exist "%ROOT%UnipusHelperPro-1.0.3.jar" (
  echo [X] UnipusHelperPro-1.0.3.jar not found next to this script.
  exit /b 1
)
if not exist "%ROOT%lib" (
  echo [!] lib\ not found. The program package needs those jars to run.
  echo     Copy lib\ from the original release next to this script first.
  echo.
  pause
)

echo Cleaning staging folder...
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%\%PKG%" 2>nul
mkdir "%STAGE%\%PKG%\docs" 2>nul

echo Copying program files...
copy /y "%ROOT%UnipusHelperPro-1.0.3.jar" "%STAGE%\%PKG%\" >nul
copy /y "%ROOT%run.bat"                   "%STAGE%\%PKG%\" >nul
copy /y "%ROOT%run.zh-CN.bat"             "%STAGE%\%PKG%\" >nul
copy /y "%ROOT%find-java.ps1"             "%STAGE%\%PKG%\" >nul
copy /y "%ROOT%unipushelper.properties.template" "%STAGE%\%PKG%\" >nul
copy /y "%ROOT%README.md"                 "%STAGE%\%PKG%\" >nul
copy /y "%ROOT%docs\*.md"                 "%STAGE%\%PKG%\docs\" >nul
if exist "%ROOT%run.sh" copy /y "%ROOT%run.sh" "%STAGE%\%PKG%\" >nul
if exist "%ROOT%lib" xcopy /e /i /q /y "%ROOT%lib" "%STAGE%\%PKG%\lib\" >nul
if exist "%ROOT%build" xcopy /e /i /q /y "%ROOT%build" "%STAGE%\%PKG%\src-patch\" >nul

echo Compressing %PKG%.zip ...
if exist "%OUT%" del /q "%OUT%"
%PWSH% "Compress-Archive -Path '%STAGE%\%PKG%' -DestinationPath '%OUT%' -CompressionLevel Optimal -Force"

echo Compressing %PKG%-src.zip ...
if exist "%STAGE%_src" rmdir /s /q "%STAGE%_src"
mkdir "%STAGE%_src\%PKG%-src" 2>nul
if exist "%ROOT%build" xcopy /e /i /q /y "%ROOT%build" "%STAGE%_src\%PKG%-src\pc-patch\" >nul
copy /y "%ROOT%run.bat" "%STAGE%_src\%PKG%-src\" >nul
copy /y "%ROOT%run.zh-CN.bat" "%STAGE%_src\%PKG%-src\" >nul
copy /y "%ROOT%find-java.ps1" "%STAGE%_src\%PKG%-src\" >nul
copy /y "%ROOT%unipushelper.properties.template" "%STAGE%_src\%PKG%-src\" >nul
copy /y "%ROOT%README.md" "%STAGE%_src\%PKG%-src\" >nul
copy /y "%ROOT%.gitignore" "%STAGE%_src\%PKG%-src\" >nul
if exist "%ROOT%docs" xcopy /e /i /q /y "%ROOT%docs" "%STAGE%_src\%PKG%-src\docs\" >nul
if exist "%OUTSRC%" del /q "%OUTSRC%"
%PWSH% "Compress-Archive -Path '%STAGE%_src\%PKG%-src' -DestinationPath '%OUTSRC%' -CompressionLevel Optimal -Force"

rmdir /s /q "%STAGE%" 2>nul
rmdir /s /q "%STAGE%_src" 2>nul

echo.
echo Done:
for %%f in ("%OUT%" "%OUTSRC%") do if exist "%%~f" for %%s in ("%%~f") do echo   %%~nxf   %%~zs bytes
echo.
pause
exit /b 0
