# 本地开发指南

## 环境准备

本地开发需要依赖中间件（Kafka, Doris, MySQL, Redis）。为了安全和一致性，请勿在本地开发时连接生产环境的数据库。

建议使用 Docker Compose 在本地起一套中间件：
1. 复制 `.env.example` 到 `.env`
2. 修改 `.env` 里的密码，可以随便设置但不要为空
3. （可选）使用 `docker-compose.yml` 启动所需服务

## 常用命令

本项目提供了一个根目录的 `Makefile` 以简化开发命令：

- 编译全部：`make build`
- 仅打包 Flink 任务：`make package-jobs`
- 运行测试：`make test`
- 检查服务状态：`make status`

## API 和 AI Engine 调试

- **Spring API**：在 IDE 中配置运行配置，确保环境变量栏里包含 `DORIS_PASSWORD` 和 `DORIS_USER`（或者通过 IntelliJ 的 EnvFile 插件加载 `.env`）。
- **AI Engine**：在 `ai-engine` 目录下，执行 `python -m venv .venv`，激活环境，并 `pip install -r requirements.txt`，然后通过 `uvicorn main:app --reload` 启动。

## Frontend 开发

在 `frontend` 目录运行：
```bash
npm install
npm run dev
```
注意：Vite 开发环境下无需真实 Nginx，但前端的 `.env.local` 绝对不能存有任何模型 API 密钥。所有对模型的调用必须通过 Spring API 或 AI Engine 代理。
