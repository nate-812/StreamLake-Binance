# StreamLake-Binance 运行配置指南

为了保障生产环境安全并统一各项服务的配置管理，本项目已废弃在代码或文档中硬编码敏感凭据的做法。

## 1. 统一环境变量标准

无论是 Java (Flink/Spring), Python (AI/Collector/Scripts) 还是 Shell 脚本，所有服务都必须通过以下统一的环境变量来加载运行所需配置及凭据：

### 核心配置变量

```bash
# Doris 数据仓库配置
DORIS_JDBC_URL=jdbc:mysql://<host>:9030/streamlake?useSSL=false
DORIS_USER=streamlake_writer      # 禁止在业务服务中使用 root
DORIS_PASSWORD=CHANGE_ME

# MySQL 风控及元数据库配置
MYSQL_HOST=192.168.1.10
MYSQL_PORT=3306
MYSQL_DATABASE=risk_control
MYSQL_USER=streamlake_app         # 禁止在业务服务中使用 root
MYSQL_PASSWORD=CHANGE_ME

# Redis 缓存与黑名单配置
REDIS_HOST=192.168.1.10
REDIS_PORT=6379
REDIS_USERNAME=streamlake_app
REDIS_PASSWORD=CHANGE_ME

# AI 引擎 API 配置
AI_ENGINE_BASE_URL=http://127.0.0.1:8000
```

## 2. 生产环境（云端）加载方式

**绝对禁止**将真实密码或 token 写入到 `.env` 文件并提交至 Git 代码库中。

在云端部署时，所有真实秘密和凭据应该存放在统一的加密路径或专门的运维机器上。当前项目规定通过 `source` 操作加载：
- 真实凭据存放路径：`/root/.streamlake-secrets`
- 所有的生产运行脚本、Flink 任务提交命令、Spring API 启动命令等，在运行前都必须先 `source /root/.streamlake-secrets` 导入以上环境变量。

## 3. 本地开发环境加载方式

**注意：此说明仅限本地开发使用，严禁将此类配置复制到云端生产环境！**

开发者可以在本地复制一份示例配置来启动依赖服务：

```bash
cp .env.example .env
```

然后将 `.env` 文件中的 `CHANGE_ME` 修改为你本地测试环境的真实值。本地代码支持一定程度的默认降级，但一旦检测到部署在生产环境，如果未注入 `PASSWORD`，程序将会启动失败，而不是静默降级为 `root` 无密码。
