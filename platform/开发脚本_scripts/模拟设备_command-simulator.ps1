<#
.SYNOPSIS
    模拟温室设备接收控制指令并回执。
.DESCRIPTION
    轮询 /api/control/commands/dispatched 获取已下发指令，短暂延时后调用
    /api/control/commands/{commandNo}/ack 回执执行结果，
    用于在没有真实设备和 MQTT 网关时演示「指令下发 → 设备执行 → 状态收敛」闭环。
    真实环境中由设备的 ack 主题经物联网服务转发到同一个回执接口。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\模拟设备_command-simulator.ps1
.EXAMPLE
    # 第一条指令回执失败，演示失败分支
    powershell -ExecutionPolicy Bypass -File .\模拟设备_command-simulator.ps1 -FailOnce
.EXAMPLE
    # 只查看待回执指令，不真正回执
    powershell -ExecutionPolicy Bypass -File .\模拟设备_command-simulator.ps1 -DryRun -Iterations 3
#>
param(
    [string]$ApiBase = 'http://localhost:8090',
    [string]$Username = 'admin',
    [string]$Password = '123',
    [int]$IntervalSeconds = 2,
    [int]$Iterations = 0,
    [switch]$FailOnce,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$credential = [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes("${Username}:${Password}")
)
$headers = @{ Authorization = "Basic $credential" }
$listEndpoint = "$ApiBase/api/control/commands/dispatched?limit=10"

Write-Host '设备指令模拟器启动' -ForegroundColor Green
Write-Host "轮询接口：$listEndpoint" -ForegroundColor DarkGray
Write-Host '指令下发由物联网接入服务完成；本脚本扮演设备回执。' -ForegroundColor DarkGray
if ($DryRun) {
    Write-Host '当前为预览模式，只列出待回执指令。' -ForegroundColor Yellow
}

$interval = [Math]::Max($IntervalSeconds, 1)
$round = 0
$shouldFail = $FailOnce.IsPresent

while ($true) {
    $round++
    $handled = 0

    try {
        $response = Invoke-RestMethod -Uri $listEndpoint -Headers $headers -TimeoutSec 20
        $pending = @($response.data)
    }
    catch {
        Write-Host "查询待回执指令失败：$($_.Exception.Message)" -ForegroundColor Red
        $pending = @()
    }

    foreach ($command in $pending) {
        if ($DryRun) {
            Write-Host ("[预览] {0} {1} {2} → {3}" -f `
                $command.commandNo, $command.greenhouseCode, `
                $command.deviceCode, $command.commandType) -ForegroundColor Cyan
            $handled++
            continue
        }

        Start-Sleep -Milliseconds 800
        $success = -not $shouldFail
        $body = @{
            success = $success
            message = if ($success) { '设备执行完成' } else { '设备执行失败：限位开关未复位' }
            executedAt = (Get-Date).ToString('s')
        } | ConvertTo-Json -Compress

        try {
            Invoke-RestMethod -Method Post `
                -Uri "$ApiBase/api/control/commands/$($command.commandNo)/ack" `
                -Headers $headers -ContentType 'application/json' `
                -Body $body -TimeoutSec 20 | Out-Null
            $status = if ($success) { '已成功' } else { '已失败' }
            Write-Host ("[{0}] {1} {2} {3}" -f `
                $status, $command.commandNo, $command.deviceCode, `
                $command.commandType) -ForegroundColor `
                $(if ($success) { 'Green' } else { 'Yellow' })
            $shouldFail = $false
        }
        catch {
            Write-Host "回执失败 $($command.commandNo)：$($_.Exception.Message)" `
                -ForegroundColor Red
        }
        $handled++
    }

    if ($Iterations -gt 0 -and $round -ge $Iterations) {
        Write-Host "已完成 $round 轮，退出。" -ForegroundColor Green
        break
    }
    if ($handled -eq 0) {
        Write-Host '.' -NoNewline -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds $interval
}
