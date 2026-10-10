param(
    [string]$PlatformRoot = (Split-Path -Parent $PSScriptRoot)
)

$composeFile = Join-Path $PlatformRoot '部署配置_deploy\docker-compose.infra.yml'

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "未找到容器编排文件：$composeFile"
}

docker compose -f $composeFile up -d

