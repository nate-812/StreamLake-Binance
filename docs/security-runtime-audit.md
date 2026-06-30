# 运行安全与事实审计报告

## 1. 服务清单与运行配置现状

以下是当前 StreamLake-Binance 项目中各服务的配置、凭据加载和安全基线情况：

| 服务名称 | 启动入口 | 配置来源 | 环境变量与默认值 | 危险/风险评估 (P0/P1/P2) |
| --- | --- | --- | --- | --- |
| **Collector** | `main.py` / systemd | `config.yaml` | 无明显环境变量，硬编码了 `192.168.1.10:9092` 等内网 IP | P2：配置硬编码，无法通过环境变量注入凭据。 |
| **Flink Kline Job** | `KlineAggregationJob.java` | 环境变量 (`System.getenv`) | `DORIS_USER` 默认 `root`，`DORIS_PASSWORD` 默认空 | P0：静默使用 root 空密码连接生产 Doris。 |
| **Flink Whale Job** | `WhaleCepJob.java` | 环境变量 (`System.getenv`) | `DORIS_USER` 默认 `root`，`DORIS_PASSWORD` 默认空<br>`MYSQL_USER` 默认 `root`，`MYSQL_PASSWORD` 默认空 | P0：静默使用 root 空密码连接生产 Doris 和 MySQL。 |
| **Flink Risk Job** | `RiskControlJob.java` | 环境变量 (`System.getenv`) | `DORIS_USER`/`MYSQL_USER` 默认 `root`，密码默认空<br>`REDIS_PASSWORD` 默认空 | P0：静默使用 root 空密码连接各数据库。 |
| **Spring API** | `streamlake-api-server` | `application.yml` | `DORIS_USER` 默认 `root`，密码默认空 | P0：默认使用 root 和空密码。 |
| **AI Engine** | `main.py` (FastAPI) | 暂未发现 (可能硬编码) | `ARK_API_KEY` 等可能散落在模块中，或无强校验 | P1：凭据注入不明确。 |
| **脚本：补数** | `backfill_klines.py` | 源码硬编码 | `DORIS_USER="root"`，`DORIS_PASSWORD=""` | P0：硬编码 root 与空密码，且缺乏二次确认（Confirm）机制。 |
| **Frontend** | 未审计 | - | - | - |
| **Kafka/Doris 等** | 中间件运维脚本 | 暂无统一 ops 脚本 | - | P1：缺乏幂等的安全操作脚本。 |

## 2. 硬编码与危险项搜索结果

在代码库中发现了多处不安全的密码硬编码或默认值（具体值已脱敏）：

- **发现疑似真实秘密：** 
  - `stream-jobs/job-kline/src/main/java/com/streamlake/kline/KlineAggregationJob.java` (行 49-50): 硬编码了默认的 root 和空密码。
  - `stream-jobs/job-whale-cep/src/main/java/com/streamlake/cep/WhaleCepJob.java` (行 70-74): 硬编码了 Doris 和 MySQL 的 root 用户及空密码。
  - `stream-jobs/job-risk-control/src/main/java/com/streamlake/risk/RiskControlJob.java` (行 66-73): 硬编码了 Doris, MySQL 的 root 及空密码，以及 Redis 的空密码。
  - `api-server/src/main/resources/application.yml` (行 12-13): 暴露了默认的 root 账号和空密码配置。
  - `scripts/backfill_klines.py` (行 19-20): 显式硬编码了 `DORIS_USER = "root"` 和 `DORIS_PASSWORD = ""`。

## 3. 修复顺序与建议

根据审计结果，推荐以下修复顺序，对应项目指令书中的各个阶段：

1. **统一环境变量 (阶段 B)**: 建立 `.env.example` 和 `docs/runtime-configuration.md`，定义标准化的配置入口。
2. **修正凭据加载 (阶段 C)**: 修改 Java Flink Job、Spring API 和 Python 脚本，**移除所有 root 空密码的默认值**。如果没有注入密码，必须显式抛错退出，不再静默连接。
3. **运维脚本改造 (阶段 D)**: 在 `ops/bin` 补充标准化的组件启动与停止脚本，确保不打印密码。
4. **DataSentry 只读接口 (阶段 E)**: 提供安全的检查脚本，供未来的 Agent 调用。
5. **可观测性与文档 (阶段 F)**: 补充日常开发的启动说明和脱敏日志方案。
6. **Doris 凭据轮换方案**: 梳理专门的 `streamlake_writer` 和 `datasentry_readonly` 角色，杜绝 root 的泛滥。

---
> 本报告作为“安全与运行事实审计”阶段（Phase A）的产出。
