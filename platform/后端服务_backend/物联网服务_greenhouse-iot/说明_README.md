# 物联网服务说明

`greenhouse-iot` 负责设备侧接入，与业务服务保持独立部署边界。

| Java 包 | 中文说明 |
| --- | --- |
| `config` | MQTT 连接参数 |
| `mqtt` | MQTT 网关接口、日志网关、Paho 客户端、主题路由与指令下发 |
| `telemetry` | 遥测解析、校验和业务接口转发 |
| `business` | 调用业务服务的统一客户端（遥测、设备状态、指令下发与回执） |

当前支持两种模式：

- `MQTT_MODE=logging`：默认模式，只记录 MQTT 配置，不连接 Broker。
- `MQTT_MODE=broker`：使用 Eclipse Paho 连接 EMQX，订阅遥测、状态与回执主题。

业务接口地址和账号通过 `BUSINESS_API_URL`、`BUSINESS_API_USERNAME`、`BUSINESS_API_PASSWORD` 配置。

遥测消息支持单个读数或 `readings` 数组，字段包括 `greenhouseCode`、`deviceCode`、`metricCode`、`value`、`unit` 和 `collectedAt`。

## 主题处理

`MqttMessageRouter` 按主题后缀分发，避免所有上行消息都被当成遥测：

| 主题 | 处理 | 转发的业务接口 |
| --- | --- | --- |
| `iot/{温室}/{设备}/telemetry` | `TelemetryIngestService` 标准化读数 | `POST /api/iot/telemetry/readings` |
| `iot/{温室}/{设备}/status` | `DeviceStatusIngestService` 解析在线状态与自检结果 | `POST /api/iot/devices/status` |
| `iot/{温室}/{设备}/ack` | `CommandAckIngestService` 解析指令编号与执行结果 | `POST /api/control/commands/{commandNo}/ack` |
| `iot/{温室}/{设备}/command` | `CommandDispatchService` 发布下行指令 | 先 `GET /api/control/commands/pending`，发布成功后 `POST /api/control/commands/{commandNo}/dispatch` |

## 指令下发

`CommandDispatchService` 默认每 3 秒轮询一次待下发指令，发布到 `app.mqtt.command-topic-template` 指定的主题，成功后把指令标记为 `DISPATCHED`；发布失败不标记，下一轮自动重试。相关配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.command-dispatch.enabled` | `true` | 是否开启轮询下发 |
| `app.command-dispatch.interval-ms` | `3000` | 轮询间隔 |
| `app.command-dispatch.batch-size` | `20` | 单轮最多下发条数 |
| `app.mqtt.ack-topic` | `iot/+/+/ack` | 设备回执订阅主题 |

指令超时不在本服务处理：业务服务的 `ControlCommandJob` 会按 `timeout_seconds` 扫描，先重试再置为 `FAILED`。
