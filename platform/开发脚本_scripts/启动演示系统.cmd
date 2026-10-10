@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0启动演示系统.ps1" %*
echo.
pause
endlocal
