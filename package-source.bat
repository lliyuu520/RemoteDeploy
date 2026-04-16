@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\package-source.ps1" %*
exit /b %errorlevel%
