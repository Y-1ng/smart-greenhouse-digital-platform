# 部署配置说明

本目录提供毕业设计本地开发和答辩演示所需的基础设施。

| 双语文件 | 中文说明 |
| --- | --- |
| `docker-compose.infra.yml` | MySQL、Redis、MinIO 和 EMQX 容器编排 |
| `.env.example` | 容器端口、账号和密码示例 |

## 启动

```powershell
docker compose -f docker-compose.infra.yml up -d
```

## 停止

```powershell
docker compose -f docker-compose.infra.yml down
```

FISCO BCOS 节点根据课程指定版本单独部署，不放入通用基础设施文件，避免不同版本的环境互相影响。

