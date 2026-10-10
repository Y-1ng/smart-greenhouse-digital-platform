# 智慧温室大棚农产品全链条数字化管理平台

本项目采用单仓库、多模块结构，覆盖 Vue 3 前端、Spring Boot 业务后端、物联网接入服务、FISCO BCOS 智能合约和本地基础设施。

## 目录结构

```text
platform/
├─ 前端应用_frontend/                     Vue 3、Three.js 首页与业务管理台
├─ 后端服务_backend/                      Spring Boot 多模块后端
│  ├─ 公共模块_greenhouse-common/         统一响应、异常和基础模型
│  ├─ 业务服务_greenhouse-api/            REST、业务编排和链上适配
│  └─ 物联网服务_greenhouse-iot/          MQTT 设备接入与遥测处理
├─ 智能合约_contracts/                    Solidity 智能合约与部署说明
├─ 部署配置_deploy/                       MySQL、Redis、MinIO、EMQX 容器配置
└─ 开发脚本_scripts/                      开发辅助脚本
```

根目录中的 `docs/` 保存项目文档，`diagrams/` 保存五张设计图（PNG + Mermaid 源文件）。工具链强约束的文件和标识，例如 `pom.xml`、Java 包名、`package.json` 和合约函数名，继续使用英文。

完整的命名映射见 `项目命名与结构说明.md`。

## 快速启动

### 方式一：演示模式（推荐，不需要 Docker）

后端提供 `demo` 配置，使用 H2 内存库并由 Flyway 自动建表，同时每 15 秒自动注入一组演示遥测，启动后即可看到实时数据。环境周期存证为手动触发：后端没有自动生成环境周期的定时任务，只在生产指挥页点击“立即生成并存证”时才会汇总四项遥测并上链。

```powershell
# 1. 构建后端（首次或改动后执行一次）
mvn -f "platform/后端服务_backend/pom.xml" -pl 业务服务_greenhouse-api -am package

# 2. 启动后端：demo 配置，H2 内存库，端口 8090
java -jar "platform/后端服务_backend/业务服务_greenhouse-api/target/greenhouse-api-0.1.0-SNAPSHOT.jar" --spring.profiles.active=demo

# 3. 启动前端
cd "platform/前端应用_frontend"
npm install
npm run dev
```

浏览器打开 `http://localhost:5199`，使用 `admin / 123` 登录。

更省事的方式：构建完成后直接运行 `platform/开发脚本_scripts/启动演示系统.cmd`。脚本会拉起业务后端、前端、物联网服务和设备指令模拟器，并打开登录页；也可以在 IDEA 中直接运行已配置好的 `后端服务_demo`，该入口同样会自动解析二维码公开地址。

手机扫码演示（推荐，地址固定）：先双击 `platform/开发脚本_scripts/启动公网隧道.cmd` 起隧道，再启动演示系统。隧道会占住账号绑定的固定域名 `https://sage-ship-scribing.ngrok-free.dev`，该域名不随重启变化，二维码印一次长期有效，手机连任何网络（4G/5G/别的 Wi-Fi）都能扫开。启动顺序不能反：`启动演示系统.cmd` 与 IDEA 的 `后端服务_demo` 只在启动时解析一次二维码地址，先起后端再起隧道的话二维码仍会指向局域网地址，需要重启后端。

地址解析规则：显式设置环境变量 `TRACE_PUBLIC_BASE_URL` 时以它为准；否则探测 ngrok 本地接口（`127.0.0.1:4040`），隧道在线就用上面的固定域名；隧道不在线则退回当前局域网 IPv4（优先取默认出口网卡，避开 VMware / WSL 虚拟网段）的 `http://<当前IP>:5199`，此时只有同一 Wi-Fi 下能扫码。前端固定监听 `0.0.0.0:5199`。浏览器（含手机扫码）首次访问固定域名会出现 ngrok 提示页，点一次 `Visit Site` 即可，之后 7 天内同一域名不再出现。首次启动时请在 Windows 防火墙弹窗中允许 Node.js 访问专用网络。

需要演示阈值告警时，把 `application-demo.yml` 的 `app.demo.telemetry.alert-demo` 改为 `true`，或执行：

```powershell
powershell -ExecutionPolicy Bypass -File "platform/开发脚本_scripts/模拟设备_telemetry-simulator.ps1" -TriggerAlert
```

如果不用一键脚本，控制指令闭环需要再启动两个进程：

```powershell
# 1. 物联网接入服务：轮询待下发指令并发布到 MQTT（logging 模式下发布动作只打日志）
java -jar "platform/后端服务_backend/物联网服务_greenhouse-iot/target/greenhouse-iot-0.1.0-SNAPSHOT.jar" --spring.main.web-application-type=none

# 2. 设备模拟器：扮演设备对已下发指令回执
powershell -ExecutionPolicy Bypass -File "platform/开发脚本_scripts/模拟设备_command-simulator.ps1"
```

只启动业务后端时，指令会停在 `CREATED`，超过 `timeout_seconds` 后进入重试，超过重试上限置为 `FAILED`，可用于演示设备离线场景。把 `MQTT_MODE` 设为 `broker` 并配置 `MQTT_BROKER_URL` 后，同一条链路走真实 MQTT（EMQX）。

### 方式二：完整环境（MySQL、Redis、MinIO、EMQX）

#### 1. 启动基础设施

```powershell
docker compose -f "platform/部署配置_deploy/docker-compose.infra.yml" up -d
```

默认端口：

| 服务 | 端口 | 默认账号 |
| --- | --- | --- |
| MySQL | `3306` | `root / root123456` |
| Redis | `6379` | 无密码，仅供本地开发 |
| MinIO API | `9000` | `minioadmin / minioadmin` |
| MinIO Console | `9001` | `minioadmin / minioadmin` |
| EMQX MQTT | `1883` | `admin / public` |
| EMQX Dashboard | `18083` | `admin / public` |

#### 2. 启动后端

```powershell
# 构建后运行；不带 spring.profiles.active 时使用 application.yml 中的 MySQL 配置
mvn -f "platform/后端服务_backend/pom.xml" -pl 业务服务_greenhouse-api -am package
java -jar "platform/后端服务_backend/业务服务_greenhouse-api/target/greenhouse-api-0.1.0-SNAPSHOT.jar"
```

后端默认地址为 `http://localhost:8080`（demo 配置为 `8090`）。开发环境默认账号为 `admin / 123`，正式开发时通过环境变量 `APP_ADMIN_USER` 和 `APP_ADMIN_PASSWORD` 覆盖。

#### 3. 启动物联网接入服务

```powershell
mvn -f "platform/后端服务_backend/pom.xml" -pl 物联网服务_greenhouse-iot -am package
java -jar "platform/后端服务_backend/物联网服务_greenhouse-iot/target/greenhouse-iot-0.1.0-SNAPSHOT.jar"
```

#### 4. 启动前端

```powershell
cd "platform/前端应用_frontend"
npm install
npm run dev
```

前端默认地址为 `http://localhost:5199`，开发服务器将 `/api` 代理到 `http://localhost:8090`；如需指向其它端口，设置环境变量 `VITE_API_PROXY_TARGET`。

## 后端模块

- `业务服务_greenhouse-api`：环境、控制、水肥、质量、溯源、合作和治理接口。
- `物联网服务_greenhouse-iot`：设备身份校验、MQTT 遥测接收、控制下发和回执处理。
- `公共模块_greenhouse-common`：统一响应、错误码和业务异常。

## 区块链运行模式

后端通过 `app.chain.mode` 选择链上实现，默认 `rest`，即把存证交易真正写入 FISCO BCOS：

| 模式 | 用途 | 说明 |
| --- | --- | --- |
| `rest`（默认） | 演示与交付 | 调用链上适配服务，回执来自真实区块，可用控制台按交易哈希复核 |
| `simulated` | 自动化测试 | 本地生成确定性回执，不依赖节点；`application-test.yml` 固定使用该模式 |

链上关系：

```text
业务服务_greenhouse-api  --POST {method, arguments}-->  链上适配服务 ChainAdapter（8600）
                                                              |
                                                              +-- FISCO BCOS 2.8.0 四节点（群组 1）
```

当前实训环境的合约地址为 `0xf763648341076819904e80d855127272f2d4e27b`，适配服务默认地址为 `http://192.168.88.141:8600`，可通过环境变量覆盖：

```powershell
# 切回本地模拟回执（不做真实上链）
$env:CHAIN_MODE = 'simulated'
java -jar "platform/后端服务_backend/业务服务_greenhouse-api/target/greenhouse-api-0.1.0-SNAPSHOT.jar" --spring.profiles.active=demo

# 指向另一台机器上的适配服务
$env:FISCO_ENDPOINT = 'http://192.168.88.150:8600'
```

节点没启动时，存证接口不会报错，链上状态写为 `PENDING` 并保留待补偿，业务数据正常落库；启动节点后重新触发存证即可。

保留为 `PENDING` 的记录不需要手工补数据：后端每 60 秒自动重试一次（环境周期、合格证、溯源码、扫码记录四类），补偿成功后回写交易哈希。治理驾驶舱的“链上存证”面板会显示适配服务状态、合约地址和各类待补偿条数，管理员和操作员可点“立即补偿”手工触发；质检员和监管账号只能查看。

验证整条链路（适配服务与节点需已启动）：

```powershell
powershell -ExecutionPolicy Bypass -File "platform/开发脚本_scripts/验证真实上链_real-chain-smoke-test.ps1"
```

合约部署、角色授权和适配服务启动命令见 `智能合约_contracts/说明_README.md`。

## 默认开发账号

| 服务 | 用户名 | 密码 |
| --- | --- | --- |
| 后端 Basic Auth | `admin` | `123` |
| MySQL | `root` | `root123456` |
| MinIO | `minioadmin` | `minioadmin` |
| EMQX Dashboard | `admin` | `public` |

这些账号只用于本地开发，进入测试或演示环境前必须通过环境变量替换。

## 当前骨架已包含

- Vue 登录页、路由守卫、状态持久化、Axios、ECharts、Three.js 首页和四个业务区域。
- Spring Boot 多模块构建、统一响应、JWT/RBAC、Flyway 数据库迁移和业务接口。
- IoT 服务的 Paho MQTT 客户端、遥测解析和业务接口转发。
- 本地文件存储与 MinIO 文件存储切换能力。
- 环境存证、溯源码、扫码记录、合格证摘要和追溯节点的 Solidity 合约，并已在 FISCO BCOS 2.8.0 节点真实部署、完成链上角色授权，四类业务存证会写入真实交易哈希。
- 控制指令下发闭环（业务指令 → 物联网服务发布 MQTT → 设备回执 → 状态收敛，含超时重试）与可选 Redis 缓存层（默认关闭，无 Redis 时自动降级）。
- MySQL、Redis、MinIO 和 EMQX 的 Docker Compose 开发环境。

## 建议开发顺序

以下主线均已完成，可作为后续扩展的基线：

1. 数据库迁移与演示数据：Flyway V1~V20（21 张表），配套 MySQL 脚本见 `docs/04_数据库初始化脚本.sql`。
2. 设备接入：IoT 服务的 MQTT 订阅、遥测转发、设备状态上报、控制指令下发与设备回执闭环（含超时重试与失败收敛），无 Broker 时可用模拟器脚本演示同一条状态机。
3. 环境存证：周期汇总、数据摘要与根哈希、链上写入，以及待补偿记录的定时重试与手工补偿。
4. 质量与溯源：检测报告摘要、Ed25519 合格证验签、一物一码、田块指针链与消费者扫码风控。
5. 前端：数字孪生首页、生产指挥、质量溯源、合作运营和治理决策五个入口，已与后端接口端到端联调。
