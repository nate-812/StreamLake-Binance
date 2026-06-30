# 故障排查手册 (Troubleshooting)

当 StreamLake-Binance 集群或其依赖出现异常时，请按照以下步骤排查。

## 1. Flink 任务无数据输出
**症状**：Doris 对应的 K 线或告警表中数据停止更新。
**排查步骤**：
1. 使用 `ops/checks/check_flink_jobs.sh` 检查任务是否处于 RUNNING 状态。
2. 检查源端 Kafka：运行 `ops/checks/check_kafka_topics.sh` 及 `kafka-console-consumer.sh`，确认 `binance.trade.raw` 中是否有源源不断的数据。
3. 检查反压与 Checkpoint：登录 Flink WebUI (通常在内网的 8081 端口)，查看对应 Job 的 Checkpoint 历史，确认是否连续失败。

## 2. API 接口响应过慢或超时
**症状**：前端访问 `/diagnose` 接口时总是 Timeout。
**排查步骤**：
1. 检查 Spring Boot 状态：`ops/bin/spring.sh status`。
2. 确认 AI Engine 是否就绪：使用 `ops/bin/ai.sh status`。如果 AI Engine DOWN，可能导致 API Server 长时间等待响应。
3. 检查 AI Engine 日志，确认 `rag-index-builder` 线程是否因 Milvus 未启动而频繁报错。

## 3. DataSentry 告警触发
当收到自动巡检产生的告警时：
- 先通过只读的检查脚本复现告警现象。
- 所有非只读修复操作（例如杀死僵尸任务、重启服务）必须通过 `ops/bin/` 中的命令附加 `--confirm` 执行。
- 绝不使用 `kill -9` 强杀 Doris 或 Kafka。
