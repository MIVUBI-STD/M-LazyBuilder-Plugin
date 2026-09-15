@echo off
call "%~dp0DEV.cmd" update %*
exit /b %ERRORLEVEL%
