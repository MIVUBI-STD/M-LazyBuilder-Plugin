@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tooling\windows-toolchain\scripts\wrappers\gradle.ps1" %*
exit /b %ERRORLEVEL%
