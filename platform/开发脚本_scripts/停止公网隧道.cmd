@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Í£Ö¹¹«ÍøËíµÀ_stop-public-tunnel.ps1" %*
echo.
pause
endlocal
