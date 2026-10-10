<#
.SYNOPSIS
    停止 ngrok 固定公网隧道。
.DESCRIPTION
    只结束本项目 output\tools\ngrok 下的 ngrok 进程，不影响系统里其它 ngrok 实例。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\停止公网隧道_stop-public-tunnel.ps1
#>

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ngrokExe = Join-Path $projectRoot 'output\tools\ngrok\ngrok.exe'

$targets = @(
    Get-CimInstance Win32_Process -Filter "Name = 'ngrok.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.ExecutablePath -and $_.ExecutablePath -ieq $ngrokExe }
)

if ($targets.Count -eq 0) {
    Write-Host '隧道没有在运行。' -ForegroundColor Yellow
    exit 0
}

foreach ($target in $targets) {
    Stop-Process -Id $target.ProcessId -Force -ErrorAction SilentlyContinue
}

Write-Host "已停止 $($targets.Count) 个 ngrok 进程，公网地址已失效（域名本身不会变）。" -ForegroundColor Green
