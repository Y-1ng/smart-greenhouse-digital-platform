<#
.SYNOPSIS
    一键启动演示系统：业务后端、前端、物联网服务和设备指令模拟器。
.DESCRIPTION
    请在 Windows 资源管理器里双击 启动演示系统.cmd 运行。
    脚本会启动业务后端、前端开发服务器、物联网服务和设备指令模拟器，
    并在启动完成后打开登录页；物联网服务默认以非 Web 模式运行。
    在 IDEA 的项目树里双击只会把 .cmd 当文本文件打开，不会执行。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\启动演示系统.ps1
.EXAMPLE
    # 后端用可见窗口启动，方便看控制台日志
    powershell -ExecutionPolicy Bypass -File .\启动演示系统.ps1 -Visible
#>
param(
    [switch]$Visible,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$java = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$apiJar = Join-Path $projectRoot `
    'platform\后端服务_backend\业务服务_greenhouse-api\target\greenhouse-api-0.1.0-SNAPSHOT.jar'
$iotJar = Join-Path $projectRoot `
    'platform\后端服务_backend\物联网服务_greenhouse-iot\target\greenhouse-iot-0.1.0-SNAPSHOT.jar'
$frontendRoot = Join-Path $projectRoot 'platform\前端应用_frontend'
$backendLog = Join-Path $PSScriptRoot '运行日志_后端.txt'
$backendErrLog = Join-Path $PSScriptRoot '运行日志_后端_错误.txt'
$iotLog = Join-Path $PSScriptRoot '运行日志_物联网.txt'
$iotErrLog = Join-Path $PSScriptRoot '运行日志_物联网_错误.txt'
$simulatorLog = Join-Path $PSScriptRoot '运行日志_设备模拟器.txt'
$simulatorErrLog = Join-Path $PSScriptRoot '运行日志_设备模拟器_错误.txt'
$simulatorScript = Join-Path $PSScriptRoot '模拟设备_command-simulator.ps1'
$javaRuntimeArguments = @()
if ($env:CODEX_SANDBOX_NETWORK_DISABLED -eq '1') {
    # Codex 沙箱禁止 Java 17 的 Unix Domain Socket 回环管道。
    # 指向不存在的目录后，JDK 会自动回退到普通 TCP 回环，正常桌面运行不受影响。
    $javaRuntimeArguments += '-Djdk.net.unixdomain.tmpdir=C:\__codex_missing_unix_socket_dir__\' +
        ('y' * 160)
}

if (-not (Test-Path -LiteralPath $java)) {
    throw '未找到 JDK 17，请检查 C:\Program Files\Java\jdk-17。'
}
if (-not (Test-Path -LiteralPath $apiJar)) {
    throw '未找到后端 JAR，请先执行 Maven package。'
}
if (-not (Test-Path -LiteralPath $iotJar)) {
    throw '未找到物联网服务 JAR，请先执行 Maven package。'
}
if (-not (Test-Path -LiteralPath $simulatorScript)) {
    throw '未找到设备指令模拟器脚本。'
}

function Get-PortOwner {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port `
        -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $connection) {
        return $null
    }
    $process = Get-CimInstance Win32_Process `
        -Filter "ProcessId=$($connection.OwningProcess)" `
        -ErrorAction SilentlyContinue
    $commandLine = ''
    if ($process -and $process.CommandLine) {
        $commandLine = $process.CommandLine
    }
    return [pscustomobject]@{
        ProcessId   = $connection.OwningProcess
        Name        = if ($process) { $process.Name } else { 'unknown' }
        CommandLine = $commandLine
    }
}

function Get-MarkerProcesses {
    param([string]$Marker)

    return @(
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.CommandLine -and $_.CommandLine.Contains($Marker)
            }
    )
}

function Get-LanIPv4 {
    # 手机扫码场景优先无线网卡（同一路由器或电脑连手机热点），
    # 避开 VMware / VirtualBox / WSL 的虚拟网段。
    try {
        $wireless = Get-NetAdapter -Physical -ErrorAction Stop |
            Where-Object {
                $_.Status -eq 'Up' -and (
                    $_.InterfaceDescription -match 'Wi-?Fi|Wireless|无线' -or
                    $_.Name -match 'WLAN|Wi-?Fi|无线'
                )
            }
        foreach ($adapter in $wireless) {
            $address = Get-NetIPAddress `
                -InterfaceIndex $adapter.ifIndex `
                -AddressFamily IPv4 -ErrorAction SilentlyContinue |
                Where-Object { $_.IPAddress -notlike '169.254.*' } |
                Select-Object -First 1
            if ($address) {
                return $address.IPAddress
            }
        }
    }
    catch {
        # 取不到无线网卡时回退默认路由。
    }
    try {
        $route = Get-NetRoute -DestinationPrefix '0.0.0.0/0' `
            -ErrorAction Stop |
            Where-Object { $_.NextHop -ne '0.0.0.0' } |
            Sort-Object RouteMetric |
            Select-Object -First 1
        if ($route) {
            $address = Get-NetIPAddress `
                -InterfaceIndex $route.InterfaceIndex `
                -AddressFamily IPv4 -ErrorAction Stop |
                Where-Object { $_.IPAddress -notlike '169.254.*' } |
                Select-Object -First 1
            if ($address) {
                return $address.IPAddress
            }
        }
    }
    catch {
        # 取不到默认路由时回退到主机名解析结果。
    }
    try {
        $fallback = [System.Net.Dns]::GetHostAddresses(
            [System.Net.Dns]::GetHostName()
        ) | Where-Object {
            $_.AddressFamily -eq 'InterNetwork' -and
            $_.IPAddressToString -notlike '127.*' -and
            $_.IPAddressToString -notlike '169.254.*'
        } | Select-Object -First 1
        if ($fallback) {
            return $fallback.IPAddressToString
        }
    }
    catch {
        # 保持默认回环地址。
    }
    return '127.0.0.1'
}

Write-Host '=== 启动演示系统 ===' -ForegroundColor Cyan
Write-Host "项目目录：$projectRoot" -ForegroundColor DarkGray
Write-Host "后端 JAR：$apiJar" -ForegroundColor DarkGray

$lanIp = Get-LanIPv4
$tracePublicBaseUrl = $env:TRACE_PUBLIC_BASE_URL
if ([string]::IsNullOrWhiteSpace($tracePublicBaseUrl)) {
    $tracePublicBaseUrl = "http://${lanIp}:5199"
}
Write-Host "局域网 IP：$lanIp" -ForegroundColor DarkGray
Write-Host "二维码公开地址：$tracePublicBaseUrl" -ForegroundColor DarkGray

$apiOwner = Get-PortOwner -Port 8090
if ($apiOwner) {
    Write-Host "后端：8090 已被占用，跳过启动（pid $($apiOwner.ProcessId) $($apiOwner.Name)）" `
        -ForegroundColor Yellow
    if ($apiOwner.CommandLine.Length -gt 0) {
        $preview = $apiOwner.CommandLine.Substring(
            0,
            [Math]::Min(140, $apiOwner.CommandLine.Length)
        )
        Write-Host "  $preview" -ForegroundColor DarkGray
    }
    if ($apiOwner.CommandLine -notlike '*greenhouse-api-0.1.0-SNAPSHOT.jar*') {
        Write-Host '  ↑ 不是本项目的 jar 实例（例如 IDEA 里运行的）；要改用 jar 请先在 IDEA 中停止。' `
            -ForegroundColor Yellow
    }
    Write-Host "  ↑ 二维码地址由正在运行的后端决定；若与 $tracePublicBaseUrl 不一致，请停止后重启后端。" `
        -ForegroundColor Yellow
}
else {
    $arguments = @($javaRuntimeArguments + @(
        '-jar', $apiJar, '--spring.profiles.active=demo'
    ))
    $arguments += "--app.trace.qr.public-base-url=$tracePublicBaseUrl"
    if ($Visible) {
        Start-Process -FilePath $java -ArgumentList $arguments `
            -WorkingDirectory $projectRoot
        Write-Host '后端：已启动（demo 配置，8090，可见窗口输出日志）' -ForegroundColor Green
    }
    else {
        Remove-Item $backendLog, $backendErrLog -ErrorAction SilentlyContinue
        Start-Process -FilePath $java -ArgumentList $arguments `
            -WorkingDirectory $projectRoot -WindowStyle Hidden `
            -RedirectStandardOutput $backendLog `
            -RedirectStandardError $backendErrLog
        Write-Host '后端：已启动（demo 配置，8090，隐藏窗口）' -ForegroundColor Green
        Write-Host "  运行日志：$backendLog" -ForegroundColor DarkGray
    }
}

$frontendOwner = Get-PortOwner -Port 5199
if ($frontendOwner) {
    Write-Host "前端：5199 已在运行，跳过启动（pid $($frontendOwner.ProcessId)）" `
        -ForegroundColor Yellow
}
else {
    Start-Process -FilePath 'npm.cmd' `
        -ArgumentList @('run', 'dev', '--', '--host', '0.0.0.0', '--port', '5199') `
        -WorkingDirectory $frontendRoot `
        -WindowStyle Hidden
    Write-Host '前端：已启动（0.0.0.0:5199，本机与局域网都可访问，隐藏窗口）' `
        -ForegroundColor Green
}

$iotProcess = $null
$iotProcesses = Get-MarkerProcesses -Marker $iotJar
if ($iotProcesses.Count -gt 0) {
    Write-Host "物联网服务：已在运行，跳过启动（pid $($iotProcesses[0].ProcessId)）" `
        -ForegroundColor Yellow
}
else {
    Remove-Item $iotLog, $iotErrLog -ErrorAction SilentlyContinue
    $iotProcess = Start-Process -FilePath $java `
        -ArgumentList @($javaRuntimeArguments + @(
            '-jar', $iotJar,
            '--spring.main.web-application-type=none'
        )) `
        -WorkingDirectory $projectRoot -WindowStyle Hidden `
        -RedirectStandardOutput $iotLog `
        -RedirectStandardError $iotErrLog `
        -PassThru
    Write-Host '物联网服务：已启动（非 Web 模式，logging MQTT，隐藏窗口）' `
        -ForegroundColor Green
    Write-Host "  运行日志：$iotLog" -ForegroundColor DarkGray
}

Write-Host '等待后端就绪（最多 45 秒）...' -ForegroundColor DarkGray
$ready = $null
for ($second = 1; $second -le 45; $second++) {
    Start-Sleep -Seconds 1
    $ready = Get-NetTCPConnection -LocalPort 8090 `
        -State Listen -ErrorAction SilentlyContinue
    if ($ready) {
        break
    }
    if ($second % 5 -eq 0) {
        Write-Host "  已等待 $second 秒..." -ForegroundColor DarkGray
    }
}

if ($ready) {
    Write-Host '后端已监听 8090，启动完成。' -ForegroundColor Green
}
else {
    Write-Host '后端未在 45 秒内监听 8090，下面是日志末尾，请据此排查：' `
        -ForegroundColor Red
    foreach ($file in @($backendLog, $backendErrLog)) {
        if (Test-Path -LiteralPath $file) {
            Write-Host "--- $file" -ForegroundColor DarkGray
            Get-Content -LiteralPath $file -Tail 15 -ErrorAction SilentlyContinue |
                ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        }
    }
}

Write-Host '等待物联网服务就绪（最多 30 秒）...' -ForegroundColor DarkGray
$iotReady = $iotProcesses.Count -gt 0
for ($second = 1; $second -le 30; $second++) {
    if ($iotProcess -and $iotProcess.HasExited) {
        break
    }
    if (Test-Path -LiteralPath $iotLog) {
        $iotLogText = Get-Content -LiteralPath $iotLog -Raw `
            -ErrorAction SilentlyContinue
        if ($iotLogText -match 'Started IotApplication') {
            $iotReady = $true
            break
        }
    }
    Start-Sleep -Seconds 1
}

if ($iotReady) {
    $simulatorProcesses = Get-MarkerProcesses `
        -Marker '模拟设备_command-simulator.ps1'
    if ($simulatorProcesses.Count -gt 0) {
        Write-Host "设备模拟器：已在运行，跳过启动（pid $($simulatorProcesses[0].ProcessId)）" `
            -ForegroundColor Yellow
    }
    else {
        Remove-Item $simulatorLog, $simulatorErrLog `
            -ErrorAction SilentlyContinue
        Start-Process -FilePath 'powershell.exe' `
            -ArgumentList @(
                '-ExecutionPolicy', 'Bypass',
                '-File', $simulatorScript,
                '-ApiBase', 'http://localhost:8090',
                '-IntervalSeconds', '2'
            ) `
            -WorkingDirectory $PSScriptRoot -WindowStyle Hidden `
            -RedirectStandardOutput $simulatorLog `
            -RedirectStandardError $simulatorErrLog
        Write-Host '设备模拟器：已启动（自动回执控制指令，隐藏窗口）' `
            -ForegroundColor Green
        Write-Host "  运行日志：$simulatorLog" -ForegroundColor DarkGray
    }
}
else {
    Write-Host '物联网服务未在 30 秒内就绪，设备开关可能无法完成回执。' `
        -ForegroundColor Red
    foreach ($file in @($iotLog, $iotErrLog)) {
        if (Test-Path -LiteralPath $file) {
            Write-Host "--- $file" -ForegroundColor DarkGray
            Get-Content -LiteralPath $file -Tail 15 `
                -ErrorAction SilentlyContinue |
                ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        }
    }
}

if (-not $NoBrowser) {
    Start-Process 'http://127.0.0.1:5199/login'
    Write-Host '已打开登录页 http://127.0.0.1:5199/login（admin / 123）' `
        -ForegroundColor Green
}

Write-Host ''
Write-Host '手机扫码（需与电脑连同一 Wi-Fi）：' -ForegroundColor Cyan
Write-Host "  二维码公开地址：$tracePublicBaseUrl" -ForegroundColor Green
Write-Host '  首次使用请在 Windows 防火墙弹窗中允许 Node.js 访问专用网络。' `
    -ForegroundColor DarkGray
Write-Host '  更换网络或 IP 后，必须在质量溯源页重新生成并下载二维码，旧二维码里仍是旧地址。' `
    -ForegroundColor Yellow

Write-Host ''
Write-Host '关闭时双击 停止演示系统.cmd（默认停止后端、物联网服务和设备模拟器）。' `
    -ForegroundColor Cyan
