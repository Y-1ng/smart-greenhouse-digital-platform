# 业务服务说明

`greenhouse-api` 是平台的核心 REST 服务，提供环境、控制、溯源、存证和治理接口。

| 目录或包 | 中文说明 |
| --- | --- |
| `modules/auth` | JWT 登录、Bearer 认证和角色解析 |
| `config` | 安全、跨域和基础配置 |
| `common` | API 异常转换 |
| `modules/environment` | 环境监测 |
| `modules/control` | 智能控制 |
| `modules/trace` | 一物一码和扫码验证 |
| `modules/proof` | 链上存证查询 |
| `modules/quality` | 检测报告备案和电子合格证验签 |
| `modules/storage` | 本地或 MinIO 文件存储适配 |
| `modules/crop` | 作物批次查询 |
| `modules/cooperation` | 村庄合作、土地、股权和分红概览 |
| `modules/greenhouse` | 温室、作物、环境和设备总览 |
| `modules/dashboard` | 生产运营和政府治理驾驶舱 |
| `modules/waterfertilizer` | 水肥计划查询、创建和执行 |
| `modules/telemetry` | IoT 遥测入库和最新值查询 |
| `modules/ai` | AI 总结与 AI 问答：组装平台真实数据、调用大模型、保存问答会话 |
| `integration/ai` | DeepSeek 对话客户端（OpenAI 兼容协议，RestClient + HttpURLConnection） |
| `integration/chain` | 区块链网关接口和交易回执模型 |
| `resources/application.yml` | 本地和容器环境配置 |

当前业务接口已接入数据库、JWT/RBAC 和 Flyway 演示数据；文件存储默认使用本地实现，可通过配置切换 MinIO；Redis 作为可选缓存（默认关闭，开启后缓存温室总览与驾驶舱聚合）；FISCO BCOS 存证走链上适配服务，支持真实上链与待补偿重试。

AI 能力走 DeepSeek 的 OpenAI 兼容接口，密钥只放在后端配置（`app.ai.api-key`，演示环境在 `application-demo.yml` 里给了默认值，正式部署用环境变量 `DEEPSEEK_API_KEY` 覆盖）。未配置密钥或调用失败时，接口不会返回 500，而是返回 `source=local` 的本地框架结论并在 `notice` 中说明原因，界面必须照实标注来源。

## AI 接口

| 方法 | 路径 | 中文说明 |
| --- | --- | --- |
| `GET` | `/api/ai/status` | 返回提供方、模型名、是否配置密钥以及当前模式（`deepseek` / `local`） |
| `POST` | `/api/ai/summary` | 按业务框架归纳平台真实数据，返回结论文本、来源、耗时与数据范围 |
| `GET` | `/api/ai/chat/history?sessionId=` | 按会话返回历史问答 |
| `POST` | `/api/ai/chat` | 提问并返回这一问一答（写入 `ai_chat_message` 表） |
| `DELETE` | `/api/ai/chat/history?sessionId=` | 清空指定会话 |

## 已实现接口

| 方法 | 路径 | 中文说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 使用数据库账号登录并返回 JWT |
| `GET` | `/api/greenhouses` | 返回可选温室列表 |
| `GET` | `/api/greenhouses/{greenhouseCode}` | 返回温室、作物、环境指标和设备总览 |
| `POST` | `/api/greenhouses` | 新增大棚并同时绑定首个作物批次 |
| `PUT` | `/api/greenhouses/{greenhouseCode}/crop` | 调整大棚当前作物、品种和种植日期 |
| `DELETE` | `/api/greenhouses/{greenhouseCode}` | 删除无设备、周期和生产数据的大棚 |
| `GET` | `/api/environment/latest` | 返回温室最新环境快照与链上状态 |
| `GET` | `/api/environment/trend` | 返回最近环境采集周期趋势 |
| `POST` | `/api/environment/cycles` | 写入环境周期、读数、摘要和根哈希 |
| `GET` | `/api/environment/thresholds` | 返回温室目标范围、告警范围和控制动作 |
| `PUT` | `/api/environment/thresholds/{metricCode}` | 更新指定指标阈值并切换自动联动 |
| `POST` | `/api/control/commands` | 创建设备控制指令并持久化参数 |
| `GET` | `/api/control/commands/{commandNo}` | 按指令编号查询执行状态 |
| `POST` | `/api/control/commands/{commandNo}/ack` | 写入设备成功或失败回执 |
| `GET` | `/api/control/commands/pending` | 待下发指令列表（物联网服务轮询） |
| `GET` | `/api/control/commands/dispatched` | 已下发未回执的指令列表 |
| `POST` | `/api/control/commands/{commandNo}/dispatch` | 标记指令已发布到设备通道 |
| `GET` | `/api/trace/{code}` | 返回合格证、追溯节点和指针链核验结果 |
| `POST` | `/api/trace/codes/{code}/scan` | 记录首次或重复扫码 |
| `POST` | `/api/quality/reports` | 上传检测报告、计算 SHA-256 并保存元数据 |
| `GET` | `/api/quality/reports/{reportNo}` | 按报告编号查询检测报告 |
| `GET` | `/api/quality/reports/{reportNo}/file` | 下载检测报告原文件 |
| `GET` | `/api/crop-batches` | 返回检测报告可关联的作物批次 |
| `POST` | `/api/certificates` | 备案电子合格证 |
| `GET` | `/api/water-fertilizer/plans` | 查询指定温室和日期的水肥计划 |
| `POST` | `/api/water-fertilizer/plans` | 创建水肥计划 |
| `POST` | `/api/water-fertilizer/plans/{planNo}/execute` | 执行水肥计划并写入执行状态 |
| `GET` | `/api/proofs/{batchId}` | 返回批次环境存证、合格证和溯源码状态 |
| `POST` | `/api/trace-codes` | 生成一物一码并绑定作物批次 |
| `GET` | `/api/trace/field/{fieldNo}/path` | 查询田块完整追溯指针链 |
| `GET` | `/api/cooperation/villages` | 返回村庄土地、股权和分红概览 |
| `POST` | `/api/cooperation/shares` | 新增村庄入股记录 |
| `POST` | `/api/cooperation/dividends` | 新增分红方案 |
| `GET` | `/api/alerts` | 查询预警记录，可按温室与状态过滤 |
| `POST` | `/api/alerts/{alertId}/ack` | 确认处理预警并记录处理人 |
| `POST` | `/api/iot/telemetry/readings` | 接收 IoT 服务转发的遥测读数 |
| `GET` | `/api/iot/telemetry/latest` | 查询温室各指标最新遥测 |
| `POST` | `/api/iot/devices/status` | 接收设备在线状态与自检结果 |
| `GET` | `/api/external/status` | 京东农场、气象、短信适配器的模式与最近调用结果 |
| `GET` | `/api/external/logs` | 外部调用留痕（请求编号、尝试次数、耗时、错误原因） |
| `POST` | `/api/external/sync` | 手动触发外部平台同步（`WEATHER`/`JD_FARM`/`SMS`/`ALL`） |
| `GET` | `/api/dashboard/government` | 返回监管驾驶舱聚合指标和风险明细（设备离线、链上待确认、重复扫码） |
| `POST` | `/api/certificates/verify` | 使用 Ed25519 公钥验证电子合格证签名 |
| `GET` | `/api/chain/status` | 链上适配服务联通状态与四类存证待补偿数量 |
| `POST` | `/api/chain/compensate` | 手工触发一次待补偿重试，返回成功/失败条数与交易哈希 |
| `POST` | `/api/trace/fields/{fieldNo}/nodes` | 追加田块追溯节点（阶段 + 说明），按指针顺序上链 |

## 演示配置说明

使用 `demo` 配置启动时（H2 内存库 + Flyway 初始化），服务会按 `app.demo.telemetry.*` 周期性注入演示遥测，启动后生产指挥界面即可看到实时变化，不需要额外脚本：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.demo.telemetry.enabled` | `true` | 是否自动注入演示遥测 |
| `app.demo.telemetry.greenhouse-codes` | 空 | 演示遥测目标温室；留空覆盖所有已配置设备的大棚，也可填写逗号分隔编号 |
| `app.demo.telemetry.interval-ms` | `15000` | 注入间隔 |
| `app.demo.telemetry.alert-demo` | `false` | 设为 `true` 时温度逐步升入告警区间 |

环境周期存证只支持手动触发：后端已移除自动汇总遥测的定时任务，不会在后台生成环境周期；只有调用 `POST /api/environment/cycles` 或 `POST /api/environment/cycles/attest`（生产指挥页“立即生成并存证”）才会汇总温度、湿度、光照、土壤水分四项读数并提交链上存证。

链上回执相关配置：

控制指令相关配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.control.timeout-scan.enabled` | `true` | 是否开启指令超时扫描；测试配置固定为 `false` |
| `app.control.timeout-scan.interval-ms` | `10000` | 扫描间隔 |
| `app.control.timeout-scan.max-retries` | `1` | 超时后最大重试次数，超过则置为 `FAILED` |
| `app.control.timeout-scan.batch-size` | `50` | 单轮最多处理条数 |
| `app.cache.enabled` | `false` | 是否启用 Redis 缓存；关闭时完全不访问 Redis |
| `app.cache.overview-ttl-seconds` | `10` | 温室总览缓存时长；设备回执成功会主动失效 |
| `app.cache.dashboard-ttl-seconds` | `30` | 监管驾驶舱聚合指标缓存时长 |
| `app.external.weather.mode` | `mock` | 气象适配器：`mock` 本地模拟 / `rest` 真实接口 |
| `app.external.sms.mode` | `mock` | 短信适配器：`mock` 本地模拟 / `rest` 真实接口 |
| `app.external.marketplace.mode` | `mock` | 京东农场适配器：`mock` 本地模拟 / `rest` 真实接口 |
| `app.external.retry.max-attempts` | `2` | 外部调用失败重试次数，结果写入 `external_sync_log` |
| `app.external.sms.alert-phone` | `13800000000` | 预警短信接收号码（演示用占位号） |
| `spring.servlet.multipart.max-file-size` | `20MB` | 检测报告等附件单文件上限，前端提示与此一致 |
| `spring.servlet.multipart.max-request-size` | `25MB` | 单次 multipart 请求上限 |

控制指令状态机：`CREATED`（已生成）→ `DISPATCHED`（物联网服务发布到设备通道）→ `SUCCEEDED`/`FAILED`（设备回执）；超过 `timeout_seconds` 未回执时回到 `CREATED` 并把 `retry_count + 1`，超过重试上限置 `FAILED`。设备状态与回执由物联网服务经 `POST /api/iot/devices/status` 和 `POST /api/control/commands/{commandNo}/ack` 写入。

文件上传超过 20 MB 时返回 400 与可读提示（不再抛 500）：`GlobalExceptionHandler` 同时处理 Spring 的 `MaxUploadSizeExceededException` 和容器包装后的超限异常。

## 缓存（Redis）

Redis 是可选组件，默认关闭：演示环境不启动 Redis 时不会产生任何连接开销。

```powershell
# 先启动 Redis（docker compose 里的服务名 redis）
docker compose -f "platform/部署配置_deploy/docker-compose.infra.yml" up -d redis
# 再带上环境变量启动后端
$env:CACHE_ENABLED = 'true'
```

开启后缓存温室总览（10 秒）与监管驾驶舱指标（30 秒）；Redis 不可用时自动回落数据库查询，不影响接口可用性。`GET /api/health` 返回 `cacheEnabled`、`cacheReachable` 两个字段便于核对。

## 外部平台适配

京东农场、气象、短信三类外部依赖都走「接口 + mock/rest 双实现」：默认 mock，切成真实接口只需改 `mode` 与 `base-url`/`token`，仓库不保存真实密钥。

- 调用留痕：所有外部调用写入 `external_sync_log`，记录请求编号、状态、尝试次数、耗时与错误原因。
- 失败隔离：外部调用失败只留痕并告警日志，不影响业务事务；短信在告警事务提交后才发送（`@TransactionalEventListener`）。
- 定时同步：`ExternalSyncJob` 默认每 10 分钟拉取一次气象预报，可用 `EXTERNAL_WEATHER_SYNC_ENABLED=false` 关闭。

链上回执相关配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.chain.mode` | `rest` | 链上网关实现：`rest` 调用链上适配服务写真实链；`simulated` 本地确定性回执，仅测试使用 |
| `app.chain.endpoint` | `http://192.168.88.141:8600` | 链上适配服务地址（环境变量 `FISCO_ENDPOINT`） |
| `app.chain.adapter-path` | `/chain/adapter` | 适配服务路径，请求体为 `{method, arguments}` |
| `app.chain.namespace-mode` | `none` | 链上业务键命名空间模式；`demo` 配置使用 `auto`，每次启动生成独立前缀 |
| `app.chain.timeout-ms` | `15000` | 适配服务连接与读取超时；真实交易含出块等待，不宜设置过短 |
| `app.chain.group-id` | `1` | FISCO BCOS 群组编号（由适配服务使用） |
| `app.chain.block-base` | `1284600` | 仅 `simulated` 模式的起始区块高度 |
| `app.chain.compensation.enabled` | `true` | 是否开启待补偿定时重试；测试配置固定为 `false` |
| `app.chain.compensation.interval-ms` | `60000` | 定时补偿间隔，无待补偿记录时不产生链上调用 |
| `app.chain.compensation.batch-size` | `20` | 每类存证单次补偿的最大条数 |

环境周期入库、合格证备案、生成一物一码、消费者扫码和追加追溯节点都会调用链上网关并把回执写入对应业务表（`chain_tx_hash` / `chain_status`）。当前实训环境的合约地址为 `0xf763648341076819904e80d855127272f2d4e27b`，适配服务实现与部署命令见 `智能合约_contracts/说明_README.md`。

链上不可用时（节点未启动、适配服务未启动）存证接口不会失败：`ChainInvoker` 捕获异常后写入 `PENDING` 并记录告警日志，业务数据照常落库。

保留为 `PENDING` 的记录由 `ChainCompensationService` 负责重试：`ChainCompensationJob` 按 `app.chain.compensation.interval-ms` 周期扫描环境周期、合格证、溯源码、扫码记录和追溯节点五类数据，补偿成功后回写 `chain_tx_hash`（环境周期同时置为 `CONFIRMED`）。补偿顺序固定为环境周期 → 合格证 → 溯源码 → 扫码记录 → 追溯节点，因为扫码记录依赖溯源码已在链上存在；追溯节点由 `TraceNodeSyncService` 按节点下标顺序推进，同下标摘要一致时走幂等重放，单条失败不影响同批其它记录。演示时可在治理驾驶舱的“链上存证”面板查看数量并点“立即补偿”。

`demo` 配置使用 H2 内存库，每次重启会重建演示数据；合约链上历史不会重建。为避免同一个周期号、合格证号、产品编码或田块编号的新内容撞上上一轮链上记录，`ChainBusinessNamespace` 会自动为本次进程生成独立业务键前缀（例如 `demo-<运行标识>-1001`）。该前缀只作用于提交给 FISCO 的业务键，数据库和前端仍显示原始业务编号。

## 数据库与测试

- Flyway 在 `src/main/resources/db/migration` 中维护 MySQL 迁移和演示数据。
- 测试使用 H2 的 MySQL 兼容模式，不依赖本机数据库。
- 使用 Java 17 运行 Maven 测试：`mvn -pl 业务服务_greenhouse-api -am test`。
- 使用 `--spring.profiles.active=demo` 可切换 H2 演示数据库，默认端口为 `8090`。
- `app.storage.provider=local` 使用 `app.storage.local-root` 保存文件，默认目录为 `./data/files`。
- `app.storage.provider=minio` 时使用 MinIO 保存文件，并自动创建 `MINIO_BUCKET`。
- JWT 由 `POST /api/auth/login` 签发，默认有效期为 120 分钟。
- 当前演示角色包括 `ADMIN`、`OPERATOR`、`QUALITY`、`GOVERNMENT`、`COOPERATIVE` 和 `IOT`。
