# DataSentry 接入指南

为了让 DataSentry Agent 能够安全、无侵入地巡检和排查故障，我们在 `ops/checks` 目录下提供了一系列标准的只读接口。

## 原则
1. **绝对只读**：此目录下的所有脚本和 SQL，都必须保证绝对只读。
2. **文本结构化**：输出优先采用稳定的文本或 JSON 格式，方便 LLM Agent 或解析器提取信息。
3. **禁止全量扫描**：对于 Redis 使用 `SCAN` 而非 `KEYS`，对于大表查询使用 `LIMIT`，防止拖垮生产库。
4. **现场路径显式配置**：Kafka、Flink、Redis 等检查脚本支持环境变量覆盖路径和连接信息；DataSentry 调用前应在目标配置中固定这些变量。

## 可用检查脚本

| 脚本/文件 | 用途 | Agent 使用建议 |
| --- | --- | --- |
| `check_streamlake_status.sh` | 检查主要 API 及 AI Engine 的健康状态 | 解析 JSON 获取 `.api` 和 `.ai` 的 UP/DOWN 状态。 |
| `check_kafka_topics.sh` | 列出 Kafka 现有 Topic 及消费组 | 用于排查“是否有数据流入”。 |
| `check_flink_jobs.sh` | 列出 Flink 正在运行的 Job ID | 用于确认实时作业存活。 |
| `check_doris_freshness.sql` | 检查 Doris 中 K 线数据的延迟分钟数 | `mysql -h... < check_doris_freshness.sql`，可用来发现任务卡死。 |
| `check_mysql_rules.sql` | 查看当前生效的风控规则 | 排查是否规则配置有误导致大量误杀。 |
| `check_redis_blacklist.sh` | 使用 SCAN 查看被封禁的风险币种 | 排查风控实时拦截情况。 |

## Future Work
- DataSentry 可以将以上检查脚本封装成 Runbook 定时执行，遇到异常则触发进一步分析。
- 自动化故障恢复 (Automated Remediation)：在只读巡检发现问题后，可评估接入 `ops/bin` 中的运维脚本；接入前必须先完成云端 smoke、权限审计和回滚演练。
