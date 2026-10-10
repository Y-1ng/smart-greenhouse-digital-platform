<#
.SYNOPSIS
    模拟温室设备向业务服务上报遥测读数。
.DESCRIPTION
    按固定间隔调用 /api/iot/telemetry/readings，写入温度、湿度、光照和土壤水分四项读数。
    业务服务的温室总览优先采用设备最新遥测值，配合生产指挥界面的自动刷新即可看到数据变化。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\模拟设备_telemetry-simulator.ps1
.EXAMPLE
    # 告警演示：温度在约两分钟内逐步升入告警区间
    powershell -ExecutionPolicy Bypass -File .\模拟设备_telemetry-simulator.ps1 -TriggerAlert
.EXAMPLE
    # 只查看将要上报的数据，不真正调用接口
    powershell -ExecutionPolicy Bypass -File .\模拟设备_telemetry-simulator.ps1 -DryRun -Iterations 3
#>
param(
    [string]$ApiBase = 'http://localhost:8090',
    [string]$Username = 'iot-service',
    [string]$Password = '123',
    [string]$GreenhouseCode = 'GH-001',
    [string]$DeviceCode = 'FAN-A-02',
    [int]$IntervalSeconds = 10,
    [int]$Iterations = 0,
    [switch]$TriggerAlert,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$credential = [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes("${Username}:${Password}")
)
$headers = @{ Authorization = "Basic $credential" }
$endpoint = "$ApiBase/api/iot/telemetry/readings"

$metrics = @(
    @{ Code = 'temperature'; Unit = 'C'; Base = 22.5; Amplitude = 2.6; Period = 240 },
    @{ Code = 'humidity'; Unit = '%'; Base = 68.0; Amplitude = 4.0; Period = 300 },
    @{ Code = 'light'; Unit = 'lx'; Base = 18600; Amplitude = 3200; Period = 180 },
    @{ Code = 'soil_moisture'; Unit = '%'; Base = 43.0; Amplitude = 3.2; Period = 360 }
)

Write-Host "遥测模拟器启动：$GreenhouseCode / $DeviceCode" -ForegroundColor Green
Write-Host "目标接口：$endpoint" -ForegroundColor DarkGray
if ($DryRun) {
    Write-Host '当前为预览模式，只输出数值不调用接口。' -ForegroundColor Yellow
}
if ($TriggerAlert) {
    Write-Host '告警演示已开启：温度约两分钟后进入告警区间。' -ForegroundColor Yellow
}

$round = 0
$interval = [Math]::Max($IntervalSeconds, 1)

while ($true) {
    $round++
    $elapsed = ($round - 1) * $interval
    $readings = @()

    foreach ($metric in $metrics) {
        $angle = 2 * [Math]::PI * ($elapsed % $metric.Period) / $metric.Period
        $value = $metric.Base + $metric.Amplitude * [Math]::Sin($angle)
        if ($TriggerAlert -and $metric.Code -eq 'temperature') {
            $value += [Math]::Min($elapsed / 120.0, 1) * 12
        }
        $value += (Get-Random -Minimum -60 -Maximum 61) / 100
        $readings += @{ Metric = $metric; Value = [Math]::Round($value, 2) }
    }

    $summary = ($readings | ForEach-Object {
        '{0} {1}{2}' -f $_.Metric.Code, $_.Value, $_.Metric.Unit
    }) -join ' · '
    $time = (Get-Date).ToString('HH:mm:ss')

    if ($DryRun) {
        Write-Host "[$time] $summary"
    }
    else {
        try {
            foreach ($reading in $readings) {
                $body = @{
                    greenhouseCode = $GreenhouseCode
                    deviceCode     = $DeviceCode
                    metricCode     = $reading.Metric.Code
                    value          = $reading.Value
                    unit           = $reading.Metric.Unit
                    collectedAt    = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
                    topic          = "iot/$GreenhouseCode/$DeviceCode/telemetry"
                } | ConvertTo-Json -Compress
                Invoke-RestMethod `
                    -Method Post `
                    -Uri $endpoint `
                    -Headers $headers `
                    -ContentType 'application/json; charset=utf-8' `
                    -Body $body | Out-Null
            }
            Write-Host "[$time] $summary -> 已上报 $($readings.Count) 项" -ForegroundColor DarkGreen
        }
        catch {
            Write-Warning "[$time] 上报失败：$($_.Exception.Message)"
        }
    }

    if ($Iterations -gt 0 -and $round -ge $Iterations) {
        break
    }
    Start-Sleep -Seconds $interval
}
