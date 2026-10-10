# 公共模块说明

`greenhouse-common` 保存多个后端服务共享的基础代码。

| Java 包 | 中文说明 |
| --- | --- |
| `com.smartgreenhouse.common.api` | 统一 API 响应 |
| `com.smartgreenhouse.common.error` | 错误码和业务异常 |

该模块不依赖 Web、数据库或物联网组件，避免公共模块反向依赖具体实现。

