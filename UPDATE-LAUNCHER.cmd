@echo off
setlocal
cd /d "%~dp0EngineData\Frontend\RustApp"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\build-local.ps1" -UpdateInstalled %*
set EXITCODE=%ERRORLEVEL%
echo.
if not "%EXITCODE%"=="0" (
  echo LazyBuilder Launcher update failed with exit code %EXITCODE%.
) else (
  echo LazyBuilder Launcher update finished successfully.
)
pause
exit /b %EXITCODE%
