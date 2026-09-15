@echo off
call "%~dp0DEV.cmd" setup %*
exit /b %ERRORLEVEL%
