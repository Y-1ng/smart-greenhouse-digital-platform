<#
.SYNOPSIS
    停止由「启动演示系统」拉起的后端、物联网服务、设备模拟器和前端进程。
.DESCRIPTION
    按端口找到监听进程，并校验命令行确实属于本项目，避免误杀 IDEA 或其它程序：
      8090  greenhouse-api jar（后端）
      8081  greenhouse-iot jar（物联网服务）
      模拟设备_command-simulator.ps1（设备模拟器）
      5199  vite 开发服务器（前端，可选）
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\停止演示系统.ps1
.EXAMPLE
    # 保留物联网服务和设备模拟器
    powershell -ExecutionPolicy Bypass -File .\停止演示系统.ps1 -KeepIot
#>
param(
    [switch]$KeepIot,
    [switch]$IncludeFrontend
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$apiJarName = 'greenhouse-api-0.1.0-SNAPSHOT.jar'
$iotJarName = 'greenhouse-iot-0.1.0-SNAPSHOT.jar'
$frontendMarker = '前端应用_frontend'
$simulatorMarker = '模拟设备_command-simulator.ps1'

function Stop-ByPort {
    param(
        [int]$Port,
        [string]$Match,
        [string]$Label
    )

    $connections = Get-NetTCPConnection -LocalPort $Port `
        -State Listen -ErrorAction SilentlyContinue
    if (-not $connections) {
        Write-Host "$Label 未在运行（端口 $Port 空闲）" -ForegroundColor DarkGray
        return
    }

    $stopped = 0
    foreach ($processId in ($connections.OwningProcess | Sort-Object -Unique)) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" `
            -ErrorAction SilentlyContinue
        if (-not $process) {
            continue
        }
        $commandLine = if ($process.CommandLine) { $process.CommandLine } else { '' }
        if ($commandLine -notlike "*$Match*") {
            Write-Host "$Label：端口 $Port 被其它程序占用（pid $processId），未处理" `
                -ForegroundColor Yellow
            Write-Host "  $($commandLine.Substring(0, [Math]::Min(120, $commandLine.Length)))" `
                -ForegroundColor DarkGray
            continue
        }
        Stop-Process -Id $processId -Force
        Write-Host "$Label 已停止（pid $processId）" -ForegroundColor Green
        $stopped++
    }

    if ($stopped -eq 0) {
        Write-Host "$Label：没有需要停止的本项目进程" -ForegroundColor DarkGray
    }
}

function Stop-ByCommandMarker {
    param(
        [string]$Marker,
        [string]$Label
    )

    $processes = @(
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.CommandLine -and
                $_.CommandLine.Contains($Marker)
            }
    )
    if ($processes.Count -eq 0) {
        Write-Host "$Label 未在运行" -ForegroundColor DarkGray
        return
    }
    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId -Force
        Write-Host "$Label 已停止（pid $($process.ProcessId)）" `
            -ForegroundColor Green
    }
}

Stop-ByPort -Port 8090 -Match $apiJarName -Label '后端服务'
if (-not $KeepIot) {
    Stop-ByCommandMarker -Marker $iotJarName -Label '物联网服务'
    Stop-ByCommandMarker -Marker $simulatorMarker -Label '设备模拟器'
}
if ($IncludeFrontend) {
    Stop-ByPort -Port 5199 -Match $frontendMarker -Label '前端开发服务器'
}

if ($KeepIot) {
    Write-Host '已保留物联网服务和设备模拟器。' -ForegroundColor DarkGray
}
if (-not $IncludeFrontend) {
    Write-Host '提示：需要一并停止前端开发服务器时加 -IncludeFrontend。' -ForegroundColor DarkGray
}
