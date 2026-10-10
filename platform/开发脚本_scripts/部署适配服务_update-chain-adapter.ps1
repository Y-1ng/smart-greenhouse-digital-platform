<#
  把新版 ChainAdapter.java 同步到链上适配服务虚拟机，并在虚拟机里重新编译、重启服务。

  用法（会自动提示输入虚拟机密码）：
      powershell -ExecutionPolicy Bypass -File .\部署适配服务_update-chain-adapter.ps1

  自定义目标：
      .\部署适配服务_update-chain-adapter.ps1 -VmHost 192.168.88.141 -VmUser root -RemoteDir '$HOME/smart-greenhouse-chain'

  脚本做的事：
      1. scp 上传仓库里的 ChainAdapter.java 与远程执行脚本到 /tmp
      2. 在虚拟机里先编译（编译失败就中止，旧服务继续跑），再停旧进程、用同样的参数重启
      3. 自检 GET /chain/block，返回 blockNumber 即成功
#>
param(
    [string]$VmHost = '192.168.88.141',
    [string]$VmUser = 'root',
    [int]$SshPort = 22,
    [string]$RemoteDir = '$HOME/smart-greenhouse-chain',
    [int]$ServicePort = 8600,
    # 虚拟机上 chain-adapter.service 需要 sudo 重启；留空则交互式输入 sudo 密码
    [string]$SudoPassword = ''
)

$ErrorActionPreference = 'Stop'

$platformRoot = Split-Path $PSScriptRoot -Parent
$source = Join-Path $platformRoot '智能合约_contracts\链上适配服务_chain-adapter-fisco\ChainAdapter.java'
$remoteScript = Join-Path $PSScriptRoot '部署适配服务_update-chain-adapter.sh'

if (-not (Test-Path -LiteralPath $source)) {
    throw "找不到链上适配服务源码：$source"
}
if (-not (Test-Path -LiteralPath $remoteScript)) {
    throw "找不到远程执行脚本：$remoteScript"
}
if (-not (Get-Command scp -ErrorAction SilentlyContinue)) {
    throw '未找到 scp（Windows 10 以上自带 OpenSSH 客户端，可在「可选功能」里安装）'
}

$target = "$VmUser@$VmHost"

Write-Host "1/2 上传源码与脚本到 $target …" -ForegroundColor Cyan
& scp -P $SshPort $source "${target}:/tmp/ChainAdapter.java"
if ($LASTEXITCODE -ne 0) { throw '上传 ChainAdapter.java 失败' }
& scp -P $SshPort $remoteScript "${target}:/tmp/update-chain-adapter.sh"
if ($LASTEXITCODE -ne 0) { throw '上传远程脚本失败' }

Write-Host "2/2 在虚拟机上编译并重启适配服务（会提示输入 SSH 密码）…" -ForegroundColor Cyan
$remoteCommand = "bash /tmp/update-chain-adapter.sh '$RemoteDir' $ServicePort /tmp/ChainAdapter.java '$SudoPassword'"
& ssh -p $SshPort $target $remoteCommand
if ($LASTEXITCODE -ne 0) {
    throw '远程编译或重启失败，请查看虚拟机里的输出与 ~/smart-greenhouse-chain/adapter.log'
}

Write-Host ''
Write-Host '完成。接下来：' -ForegroundColor Green
Write-Host '  1. 重启后端（业务服务），/api/chain/status 会带上 network.blockNumber'
Write-Host '  2. 打开质量溯源任意子页面，顶部会出现「当前区块高度」卡片'
