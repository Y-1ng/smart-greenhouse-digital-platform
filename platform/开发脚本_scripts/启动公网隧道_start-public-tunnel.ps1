<#
.SYNOPSIS
    启动 ngrok 固定公网隧道，让二维码在任何网络下都能扫码打开。
.DESCRIPTION
    用账号绑定的固定域名（重启不变）把本机 5199 端口映射到公网。
    隧道起来之后，业务后端的演示总控会自动探测到该地址并写进二维码，
    所以正确顺序是：先跑本脚本，再启动演示系统。
    浏览器首次访问会出现 ngrok 提示页，点一次 Visit Site 即可。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\启动公网隧道_start-public-tunnel.ps1
.EXAMPLE
    # 映射到别的本地端口
    powershell -ExecutionPolicy Bypass -File .\启动公网隧道_start-public-tunnel.ps1 -Port 5199
#>
param(
    [int]$Port = 5199
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ngrokExe = Join-Path $projectRoot 'output\tools\ngrok\ngrok.exe'
$ngrokConfig = Join-Path $projectRoot 'output\tools\ngrok\ngrok.yml'
$logDir = Join-Path $projectRoot 'output\tools'
$logFile = Join-Path $logDir 'ngrok.log'
$errFile = Join-Path $logDir 'ngrok.err.log'
$tunnelsApi = 'http://127.0.0.1:4040/api/tunnels'

function Get-TunnelPublicUrl {
    try {
        $response = Invoke-RestMethod -Uri $tunnelsApi -TimeoutSec 2 -ErrorAction Stop
    } catch {
        return $null
    }
    $tunnel = $response.tunnels |
        Where-Object { $_.public_url -like 'https://*' } |
        Select-Object -First 1
    if ($tunnel) {
        return $tunnel.public_url
    }
    return $null
}

if (-not (Test-Path $ngrokExe)) {
    Write-Host "未找到 ngrok 客户端：$ngrokExe" -ForegroundColor Red
    Write-Host '请把 ngrok.exe 放到该路径，或用 winget install --id Ngrok.Ngrok 安装后复制过去。' -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $ngrokConfig)) {
    Write-Host "未找到 ngrok 配置文件：$ngrokConfig" -ForegroundColor Red
    Write-Host '该文件保存 authtoken，缺失会导致 ERR_NGROK_4018 认证失败。' -ForegroundColor Yellow
    exit 1
}

$existingUrl = Get-TunnelPublicUrl
if ($existingUrl) {
    Write-Host "隧道已经在运行：$existingUrl" -ForegroundColor Green
    exit 0
}

# 本地服务没起来也能建隧道，但扫码会打不开，所以提前提醒一句。
$listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $listening) {
    Write-Host "提示：本机 $Port 端口暂时没有服务在监听，隧道可以先建，等前端起来后扫码才能打开。" -ForegroundColor Yellow
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Start-Process -FilePath $ngrokExe `
    -ArgumentList @('http', "$Port", '--config', "`"$ngrokConfig`"", '--log=stdout') `
    -WindowStyle Hidden `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errFile | Out-Null

$publicUrl = $null
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    $publicUrl = Get-TunnelPublicUrl
    if ($publicUrl) {
        break
    }
}

if (-not $publicUrl) {
    Write-Host '隧道启动失败，下面是 ngrok 报的错：' -ForegroundColor Red
    Get-Content $errFile -Tail 10 -ErrorAction SilentlyContinue |
        Where-Object { $_.Trim() } |
        ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
    Get-Content $logFile -Tail 30 -ErrorAction SilentlyContinue |
        Where-Object { $_ -match 'lvl=eror|lvl=crit|ERR_NGROK' } |
        Select-Object -First 5 |
        ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
    Write-Host "完整日志：$logFile" -ForegroundColor Gray
    exit 1
}

Write-Host ''
Write-Host "固定公网地址：$publicUrl" -ForegroundColor Green
Write-Host '该域名绑定在 ngrok 账号上，重启不会变，二维码不需要重印。' -ForegroundColor Green
Write-Host '接下来启动演示系统即可；关闭隧道请运行 停止公网隧道.cmd。' -ForegroundColor Gray
