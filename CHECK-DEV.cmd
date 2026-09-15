@echo off
call "%~dp0DEV.cmd" check %*
exit /b %ERRORLEVEL%
