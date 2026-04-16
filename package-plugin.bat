@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\package-plugin.ps1" %*
exit /b %errorlevel%
