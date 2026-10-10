# 智慧温室大棚农产品全链条数字化管理平台

本仓库包含毕业设计项目的总体方案、系统文档、设计图、前端应用、Spring Boot 后端、物联网接入服务、智能合约和本地部署配置。

## 入口

- 项目启动与结构说明：[`platform/项目说明_README.md`](platform/项目说明_README.md)
- 总体方案与设计文档：[`docs/`](docs/)
- 系统设计图：[`diagrams/`](diagrams/)
- 前端应用：[`platform/前端应用_frontend/`](platform/前端应用_frontend/)
- 后端服务：[`platform/后端服务_backend/`](platform/后端服务_backend/)
- 智能合约：[`platform/智能合约_contracts/`](platform/智能合约_contracts/)

## 技术栈

Vue 3、TypeScript、Vite、Three.js、Spring Boot、MySQL、Redis、MinIO、EMQX、FISCO BCOS 和 Solidity。

## 敏感配置

仓库只保留 `.env.example` 和环境变量占位符。API Key、JWT Secret、数据库密码和 ngrok token 等真实凭据必须通过本机环境变量或未提交的本地配置提供。

