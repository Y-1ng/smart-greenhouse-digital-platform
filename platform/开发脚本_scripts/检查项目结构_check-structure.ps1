param(
    [string]$PlatformRoot = (Split-Path -Parent $PSScriptRoot)
)

$expectedPaths = @(
    '前端应用_frontend',
    '后端服务_backend',
    '后端服务_backend\公共模块_greenhouse-common',
    '后端服务_backend\业务服务_greenhouse-api',
    '后端服务_backend\物联网服务_greenhouse-iot',
    '智能合约_contracts',
    '部署配置_deploy',
    '开发脚本_scripts'
)

$missing = @()
foreach ($relativePath in $expectedPaths) {
    $fullPath = Join-Path $PlatformRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath)) {
        $missing += $relativePath
    }
}

if ($missing.Count -gt 0) {
    Write-Host '项目结构不完整，缺少以下目录：' -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  - $_" }
    exit 1
}

Write-Host '项目结构检查通过。' -ForegroundColor Green

