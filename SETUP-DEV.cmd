@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tooling\windows-toolchain\scripts\bootstrap\setup-dev.ps1" %*
set EXITCODE=%ERRORLEVEL%
echo.
if not "%EXITCODE%"=="0" (
  echo LazyBuilder developer setup failed with exit code %EXITCODE%.
) else (
  echo LazyBuilder developer environment is ready.
)
pause
exit /b %EXITCODE%
