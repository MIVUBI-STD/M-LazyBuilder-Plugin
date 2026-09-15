@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tooling\windows-toolchain\scripts\verify\test-local.ps1" %*
set EXITCODE=%ERRORLEVEL%
echo.
if not "%EXITCODE%"=="0" (
  echo LazyBuilder local acceptance failed with exit code %EXITCODE%.
) else (
  echo LazyBuilder local acceptance passed.
)
pause
exit /b %EXITCODE%
