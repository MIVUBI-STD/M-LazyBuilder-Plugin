@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tooling\windows-toolchain\scripts\wrappers\maven.ps1" %*
exit /b %ERRORLEVEL%
