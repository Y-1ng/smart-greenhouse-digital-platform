# 后端服务说明

后端采用 Maven 多模块结构，公共能力单独抽出，业务接口和物联网接入分别运行。

| 双语目录 | 技术名 | 中文说明 |
| --- | --- | --- |
| `公共模块_greenhouse-common` | `greenhouse-common` | 统一响应、错误码和业务异常 |
| `业务服务_greenhouse-api` | `greenhouse-api` | REST 接口、业务编排、权限和链上适配 |
| `物联网服务_greenhouse-iot` | `greenhouse-iot` | MQTT 设备接入、遥测解析和控制回执 |

## 构建

```powershell
mvn clean install
```

## 运行

```powershell
# 业务服务：demo 配置使用 H2 内存库（端口 8090），不带 profile 时读取 application.yml 的 MySQL 配置
java -jar 业务服务_greenhouse-api/target/greenhouse-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo

# 物联网服务：默认 logging 模式只打印报文，切换 broker 模式后连接 MQTT
java -jar 物联网服务_greenhouse-iot/target/greenhouse-iot-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none
```

## IDEA 统一演示入口

运行配置 `后端服务_demo` 已改为演示总控入口 `DemoAllInOneApplication`：

- 业务后端在当前 JVM 中以 `demo` 配置启动，仍可正常打断点和调试。
- 自动启动物联网服务、设备指令模拟器和前端开发服务器。
- 已经运行的组件会自动跳过，不会重复启动。
- 停止或重启 `后端服务_demo` 时，由它启动的子进程会一起关闭。
- 日志分别写入 `platform/开发脚本_scripts/运行日志_IDEA物联网.txt`、`运行日志_IDEA设备模拟器.txt` 和 `运行日志_IDEA前端.txt`。

注意：`mvn -pl 模块 -am spring-boot:run` 会在父 POM 上执行 `spring-boot:run` 并报“Unable to find a suitable main class”，因此请先 `mvn package` 再用 `java -jar` 运行，或在 IDEA 中运行 `后端服务_demo` 运行配置。
