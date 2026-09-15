@echo off
call "%~dp0DEV.cmd" test %*
exit /b %ERRORLEVEL%
