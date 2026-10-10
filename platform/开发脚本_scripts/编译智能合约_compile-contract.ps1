<#
.SYNOPSIS
    编译智慧温室存证合约并输出 ABI 与字节码统计。
.DESCRIPTION
    使用 solc-js（默认 0.8.26）编译 智慧温室存证合约_SmartGreenhouseRegistry.sol，
    输出 ABI 文件到合约目录，并打印函数数量与字节码大小。
    首次运行需要 Node.js 与 npm，可访问 npm 源；solc 会安装在临时目录，不污染仓库。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\编译智能合约_compile-contract.ps1
.EXAMPLE
    # 已安装过 solc 时跳过安装步骤
    powershell -ExecutionPolicy Bypass -File .\编译智能合约_compile-contract.ps1 -SkipInstall
#>
param(
    [string]$SolcVersion = '0.8.26',
    [string]$ContractPath,
    [string]$OutputAbiPath,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'

$scriptRoot = $PSScriptRoot
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptRoot)
if (-not $ContractPath) {
    $ContractPath = Join-Path $projectRoot `
        'platform\智能合约_contracts\智慧温室存证合约_SmartGreenhouseRegistry.sol'
}
if (-not $OutputAbiPath) {
    $contractDir = Split-Path -Parent $ContractPath
    $OutputAbiPath = Join-Path $contractDir `
        '智慧温室存证合约_SmartGreenhouseRegistry.abi.json'
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw '未找到 Node.js，请先安装 Node.js 18 以上版本。'
}
if (-not (Test-Path -LiteralPath $ContractPath)) {
    throw "未找到合约文件：$ContractPath"
}

$solcRoot = Join-Path $env:TEMP "smart-greenhouse-solc-$SolcVersion"
$solcModule = Join-Path $solcRoot 'node_modules\solc'
if (-not (Test-Path -LiteralPath (Join-Path $solcModule 'package.json'))) {
    if ($SkipInstall) {
        throw "未找到 solc 模块：$solcModule"
    }
    Write-Host "正在安装 solc $SolcVersion ..." -ForegroundColor Yellow
    $cache = Join-Path $env:TEMP 'smart-greenhouse-npm-cache'
    & npm.cmd install "solc@$SolcVersion" --prefix $solcRoot --cache $cache --no-audit --no-fund --loglevel=error
    if ($LASTEXITCODE -ne 0) {
        throw 'solc 安装失败，请检查 npm 网络与缓存目录权限。'
    }
}

$compileScript = Join-Path $env:TEMP 'smart-greenhouse-compile-contract.cjs'
$compileTemplate = @'
const fs = require('node:fs')
const solc = require(process.env.SOLC_PATH)

const contractPath = process.env.CONTRACT_PATH
const abiOutputPath = process.env.ABI_OUTPUT_PATH
const source = fs.readFileSync(contractPath, 'utf8')

const input = {
  language: 'Solidity',
  sources: { 'SmartGreenhouseRegistry.sol': { content: source } },
  settings: {
    optimizer: { enabled: true, runs: 200 },
    outputSelection: {
      '*': { '*': ['abi', 'evm.bytecode.object', 'evm.deployedBytecode.object'] },
    },
  },
}

const output = JSON.parse(solc.compile(JSON.stringify(input)))
const messages = output.errors || []
const errors = messages.filter((item) => item.severity === 'error')
const warnings = messages.filter((item) => item.severity !== 'error')

for (const warning of warnings) {
  console.log('WARN: ' + warning.formattedMessage.trim())
}
for (const error of errors) {
  console.log('ERROR: ' + error.formattedMessage.trim())
}
if (errors.length > 0) {
  process.exit(1)
}

const contract = output.contracts['SmartGreenhouseRegistry.sol'].SmartGreenhouseRegistry
fs.writeFileSync(abiOutputPath, JSON.stringify(contract.abi, null, 2), 'utf8')

const functions = contract.abi
  .filter((item) => item.type === 'function')
  .map((item) => item.name)
  .sort()

console.log('solc 版本：' + solc.version())
console.log('警告数量：' + warnings.length)
console.log('函数数量：' + functions.length)
console.log('函数列表：' + functions.join(', '))
console.log('运行时代码大小：' + contract.evm.deployedBytecode.object.length / 2 + ' 字节')
console.log('ABI 已写入：' + abiOutputPath)
'@

Set-Content -Path $compileScript -Value $compileTemplate -Encoding UTF8

$env:SOLC_PATH = ($solcModule -replace '\\', '/')
$env:CONTRACT_PATH = ($ContractPath -replace '\\', '/')
$env:ABI_OUTPUT_PATH = ($OutputAbiPath -replace '\\', '/')

& node $compileScript
if ($LASTEXITCODE -ne 0) {
    throw '合约编译失败，请检查上面的错误信息。'
}

Write-Host '合约编译通过。' -ForegroundColor Green
