@echo off
call "%~dp0DEV.cmd" build %*
exit /b %ERRORLEVEL%
