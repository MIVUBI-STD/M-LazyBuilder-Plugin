@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tooling\windows-toolchain\scripts\bootstrap\check-tools.ps1" %*
set EXITCODE=%ERRORLEVEL%
pause
exit /b %EXITCODE%
