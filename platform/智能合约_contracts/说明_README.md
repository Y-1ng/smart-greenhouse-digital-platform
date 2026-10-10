# 智能合约说明

`contracts` 保存 FISCO BCOS 课程项目使用的 Solidity 智能合约骨架。

| 双语文件 | 中文说明 |
| --- | --- |
| `智慧温室存证合约_SmartGreenhouseRegistry.sol` | 环境根哈希、合格证摘要、溯源码、扫码记录和追溯节点存证 |
| `智慧温室存证合约_SmartGreenhouseRegistry.abi.json` | 合约编译产出的 ABI，供部署脚本与链上适配层使用 |
| `智慧温室存证合约_SmartGreenhouseRegistry.bin` | 部署用字节码，与 ABI 配套 |
| `链上适配服务_chain-adapter-fisco/ChainAdapter.java` | 链上适配服务：用 Java SDK 签名发交易，对外提供 REST 接口 |

## 合约边界

- 链上只保存根哈希、签名摘要、状态、角色和必要元数据。
- 原始环境数据、证书正文、检测报告和图片保存在链下。
- 后端和合约双重校验角色，公开函数只返回允许公开的验证结果。
- `previousHash` 用于校验田块追溯节点是否连续。
- 首节点使用 `bytes32(0)` 作为创世摘要。
- 角色用 `mapping(address => mapping(Role => bool))` 存储，一个账户可同时持有多个角色；`onlyRole` 同时放行 `ADMIN`。
- `recordEnvironmentRoot`、`recordCertificate`、`registerTraceCode` 按业务标识幂等：同一标识内容一致时允许重复上链，内容不一致时回滚。
- `appendTraceNode` 按「田块 + 下标」幂等：同下标摘要一致为幂等重放；下标等于链上长度时要求 `previousHash` 等于链上链尾，跳序或改写历史都会被拒绝。这样链下演示库重建后重新同步历史节点不会整批失败。
- `demo` 配置会给链上业务键增加每次启动独立的命名空间前缀，避免 H2 演示库重建后，同编号但不同内容的数据与持久链历史冲突；生产环境使用持久数据库时保持无前缀模式。

## 编译与部署

合约声明 `pragma solidity ^0.8.20`，已用 solc `0.8.26`（optimizer 200 runs，EVM `istanbul`）编译验证：**0 错误 0 警告，16 个外部函数，运行时代码 9434 字节（创建字节码 9578 字节）**。

```powershell
# 编译并生成 ABI（首次运行会安装 solc 到临时目录）
powershell -ExecutionPolicy Bypass -File ..\开发脚本_scripts\编译智能合约_compile-contract.ps1
```

## 实训环境部署记录（FISCO BCOS 2.8.0）

链环境：FISCO BCOS 2.8.0 四节点单群组，群组编号 1，channel 端口 `20200`–`20203`；官方控制台使用 Java SDK 2.9.1。

| 项目 | 取值 |
| --- | --- |
| 合约地址 | `0xf763648341076819904e80d855127272f2d4e27b` |
| 部署交易 | `0x28b47e9645a4d87a2aef4878b823b9f7da26532b72148f9bdf05ed665883e93f`（区块 164） |
| 服务账户 | `0x3d3c51f5f8d7c9f969aff35e31cc15452e0781bd` |
| 角色授权 | `DEVICE_SERVICE`、`QUALITY_ADMIN`、`BUSINESS_SERVICE` 三个角色已通过 `setRole` 授权给服务账户（交易 `0xfd87f960…93ca0`、`0xc942f471…d9764`、`0xd6c20b95…c975b`） |

部署与授权由适配服务自带命令完成（在机房/虚拟机的工作目录执行）：

```bash
cd ~/smart-greenhouse-chain
javac -encoding UTF-8 -cp "lib/*" -d out ChainAdapter.java
java -Djdk.tls.namedGroups=SM2,secp256k1,x25519,secp256r1,secp384r1,secp521r1 \
     -cp "out:conf/:lib/*" ChainAdapter \
     --action deploy --config conf/config.toml \
     --abi contract.abi.json --bin contract.bin --group 1 \
     --grant DEVICE_SERVICE:0x3d3c...,QUALITY_ADMIN:0x3d3c...,BUSINESS_SERVICE:0x3d3c...
```

启动适配服务（`--address` 用部署输出的合约地址）：

```bash
setsid nohup java -Djdk.tls.namedGroups=SM2,secp256k1,x25519,secp256r1,secp384r1,secp521r1 \
  -cp "out:conf/:lib/*" ChainAdapter --action serve --config conf/config.toml \
  --abi contract.abi.json --bin contract.bin \
  --address 0xf763648341076819904e80d855127272f2d4e27b --port 8600 \
  > adapter.log 2>&1 < /dev/null &
```

`-Djdk.tls.namedGroups` 必须带上：FISCO BCOS 2.8.0 节点使用 `secp256k1` 等曲线，缺少该参数会卡在 TLS 握手。

### 实训虚拟机上由 systemd 托管

虚拟机里适配服务已经做成开机自启的 systemd 单元 `chain-adapter.service`（`User=admin`，`WorkingDirectory=/home/admin/smart-greenhouse-chain`，`Restart=on-failure`）。**升级代码时要走 systemd，不要再手动 `setsid java`**，否则会和 systemd 的实例抢 8600 端口，出现一个空转的僵尸进程。

标准升级步骤（在虚拟机里）：

```bash
cd ~/smart-greenhouse-chain
# 把仓库里的 ChainAdapter.java 覆盖到当前目录
javac -encoding UTF-8 -cp "lib/*" -d out ChainAdapter.java
sudo systemctl restart chain-adapter
curl -s http://127.0.0.1:8600/chain/block     # 返回 blockNumber 即成功
```

Windows 侧可以用现成脚本一条命令完成（自动上传、编译、systemctl 重启并自检）：

```powershell
cd platform\开发脚本_scripts
powershell -ExecutionPolicy Bypass -File ".\部署适配服务_update-chain-adapter.ps1" `
  -VmHost 192.168.88.141 -VmUser admin -SudoPassword <虚拟机密码>
```

## 适配服务接口

| 接口 | 说明 |
| --- | --- |
| `GET /health` | 返回 `{status, mode, contractAddress}` |
| `GET /chain/block` | 只读查询当前区块高度、最新区块时间与近期平均出块间隔，返回 `{blockNumber, blockTimestampMs, avgBlockIntervalMs, sampleSize}`（时间戳统一为 epoch 毫秒） |
| `POST /chain/adapter` | 入参 `{"method": "...", "arguments": {...}}`，返回 `{transactionHash, blockNumber, status}` |

支持的方法与合约写方法对应：`recordEnvironmentRoot`、`recordCertificate`、`registerTraceCode`、`appendScanRecord`、`appendTraceNode`（入参含 `index`）。合约回滚时返回 HTTP 409 与中文原因，适配服务自身异常返回 500，参数不合法返回 400。

`GET /chain/block` 供业务后端展示真实链上指标（前端「当前区块高度」卡片）。修改 `ChainAdapter.java` 后需要按上面的部署命令重新编译并重启适配服务，前端才会出现该卡片；没重启时业务后端会如实返回 `available=false`，界面不显示区块高度，也不会用模拟值顶替。

注意该节点返回的区块时间戳是**毫秒**（十六进制字符串），适配服务按量级自动识别（> 1e11 视为毫秒，否则按秒换算），避免平均出块间隔被算成一千倍。

联通性验证脚本：`..\开发脚本_scripts\验证真实上链_real-chain-smoke-test.ps1`。
