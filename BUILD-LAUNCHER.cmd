@echo off
setlocal
cd /d "%~dp0apps\launcher"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\build-local.ps1" %*
set EXITCODE=%ERRORLEVEL%
echo.
if not "%EXITCODE%"=="0" (
  echo LazyBuilder Launcher build failed with exit code %EXITCODE%.
) else (
  echo LazyBuilder Launcher build finished successfully.
)
pause
exit /b %EXITCODE%
