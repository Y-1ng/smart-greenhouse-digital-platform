<#
.SYNOPSIS
    真实上链冒烟测试：检查链上适配服务与 FISCO BCOS 节点连通性，并写入五类存证交易。
.DESCRIPTION
    适配服务运行在装了 FISCO BCOS 节点的机器上（默认 8600 端口），
    本脚本从业务后端所在机器运行，验证「后端 -> 适配服务 -> 链节点」整条链路。
.EXAMPLE
    .\验证真实上链_real-chain-smoke-test.ps1
    .\验证真实上链_real-chain-smoke-test.ps1 -Adapter http://192.168.88.141:8600 -Tag 20260919001
#>
param(
    [string]$Adapter = 'http://192.168.88.141:8600',
    [string]$Tag = (Get-Date -Format 'MMddHHmmss')
)

$ErrorActionPreference = 'Stop'

function 调用链上适配服务 {
    param(
        [string]$Method,
        [hashtable]$Arguments
    )
    $payload = @{ method = $Method; arguments = $Arguments } |
        ConvertTo-Json -Depth 5 -Compress
    return Invoke-RestMethod -Method Post -Uri "$Adapter/chain/adapter" `
        -ContentType 'application/json' -Body $payload -TimeoutSec 90
}

function 输出回执 {
    param([string]$Name, $Receipt)
    $ok = $Receipt.status -eq 'CONFIRMED' -and $Receipt.transactionHash -like '0x*'
    $color = if ($ok) { 'Green' } else { 'Red' }
    $flag = if ($ok) { '通过' } else { '失败' }
    Write-Host ("[{0}] {1} -> tx={2} 区块={3} 状态={4}" -f `
        $flag, $Name, $Receipt.transactionHash, $Receipt.blockNumber, $Receipt.status) `
        -ForegroundColor $color
    if (-not $ok) { throw "$Name 未取得真实链上回执" }
}

Write-Host '== 1. 适配服务健康检查 ==' -ForegroundColor Cyan
$health = Invoke-RestMethod -Method Get -Uri "$Adapter/health" -TimeoutSec 20
if ($health.status -ne 'UP') { throw "适配服务状态异常：$($health.status)" }
Write-Host ("    适配模式：{0}，合约地址：{1}" -f $health.mode, $health.contractAddress)

$hexA = 'a' * 64
$hexB = 'b' * 64
$hexC = 'c' * 64
$zero = '0' * 64

Write-Host '== 2. 写入五类存证交易 ==' -ForegroundColor Cyan

输出回执 -Name '环境周期存证' -Receipt (调用链上适配服务 -Method 'recordEnvironmentRoot' -Arguments @{
    cycleId = $Tag; rootHash = $hexA; metadata = "digest-$Tag"
})

Write-Host '    重复提交相同内容（应幂等成功）...' -ForegroundColor DarkGray
输出回执 -Name '环境周期重复存证' -Receipt (调用链上适配服务 -Method 'recordEnvironmentRoot' -Arguments @{
    cycleId = $Tag; rootHash = $hexA; metadata = "digest-$Tag"
})

输出回执 -Name '合格证存证' -Receipt (调用链上适配服务 -Method 'recordCertificate' -Arguments @{
    certificateId = "CERT-$Tag"; contentHash = $hexB; status = 'EFFECTIVE'
})

输出回执 -Name '一物一码存证' -Receipt (调用链上适配服务 -Method 'registerTraceCode' -Arguments @{
    productCode = "SG-2026-$Tag"; signatureHash = $hexC; batchId = "BATCH-$Tag"
})

输出回执 -Name '扫码记录存证' -Receipt (调用链上适配服务 -Method 'appendScanRecord' -Arguments @{
    productCode = "SG-2026-$Tag"; channel = 'WECHAT_MINI'; result = 'FIRST_SCAN'
})

输出回执 -Name '追溯节点存证' -Receipt (调用链上适配服务 -Method 'appendTraceNode' -Arguments @{
    fieldNo = "FIELD-$Tag"; index = '0'; nodeHash = $hexA; previousHash = $zero
})

Write-Host '== 3. 防篡改校验（同一周期改内容，链上应拒绝）==' -ForegroundColor Cyan
try {
    调用链上适配服务 -Method 'recordEnvironmentRoot' -Arguments @{
        cycleId = $Tag; rootHash = ('9' * 64); metadata = 'tampered'
    } | Out-Null
    throw '链上未拒绝内容不一致的重复存证，请检查合约幂等逻辑'
}
catch {
    if ($_.Exception.Message -like '*未拒绝*') { throw }
    Write-Host ("    已按预期拒绝：{0}" -f $_.Exception.Message) -ForegroundColor Green
}

Write-Host ''
Write-Host '真实上链冒烟测试通过。' -ForegroundColor Green
Write-Host '可用 FISCO 控制台复核回执：console.sh 1 getTransactionReceipt <交易哈希>'
