# 监控与告警模板

为了提升可观测性，我们建议将以下指标接入 Prometheus 并配置告警规则。

## 1. 核心 Web Vitals 与服务健康度

- **Spring API**：暴露了 `/actuator/prometheus`。应配置告警：`up{job="streamlake-api"} == 0`，持续 1 分钟即告警。
- **AI Engine**：通过访问 `/health` 进行 HTTP 心跳监控。

## 2. 核心业务监控指标

**Kafka Topic Offset 堆积**
通过 Kafka Exporter 监控：
- `kafka_consumergroup_lag > 5000` (持续 3 分钟)

**Doris 数据新鲜度**
通过定时执行 `ops/checks/check_doris_freshness.sql` 监控，如果 K 线的 `lag_minutes > 3`，应触发 P1 级别告警，表明 Flink 到 Doris 的链路出现了拥堵或宕机。

**Flink Job 状态**
通过 Flink metrics 监控 `flink_jobmanager_job_uptime` 或者 Flink REST API。若无 RUNNING 状态的 `streamlake-kline-aggregation` 任务，必须触发告警。

## 3. 日志规范
- 统一日志输出到文件而非标准输出（Systemd 除外）。
- 任何情况下，**不得在日志中打印密码、Token 等敏感信息**。
- 若服务崩溃，可以使用 `tail -n 200 /var/log/streamlake/api.log` 快速排查。
