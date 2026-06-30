# 灾难恢复手册 (Recovery)

## 1. Flink 状态丢失或任务崩溃
若 Flink 任务发生不可逆的崩溃且 Checkpoint 损坏，导致作业需要从头开始：

**恢复步骤**：
1. 彻底停掉发生故障的旧任务：`./ops/bin/job.sh stop kline --confirm` (或者通过 Flink UI 取消)。
2. 确认 Doris 表中的最后一条正常数据的时间戳，使用 `ops/checks/check_doris_freshness.sql` 查询。
3. 若有中间缺失的数据，可以使用 `scripts/backfill_klines.py` 进行重放补数（**必须仔细评估影响范围，并附加 `--confirm`**）。
4. 重新启动 Flink 任务：`./ops/bin/job.sh start kline`。此时 Flink 将从 Kafka 的 `latest` offset 或者配置的指定策略重新开始消费。

## 2. 数据库故障回滚
如果 Doris 发生了严重的写坏现象（例如补数时发生重复写入导致 Unique Key 被脏数据覆盖）：

1. **绝对不要**使用 Flink 继续向脏表写入。第一时间停掉所有 Flink Writer (`./ops/bin/job.sh stop ...`)。
2. 评估是进行分区清空并重新消费，还是进行整表重建。
3. 清理完成后，再启动 Flink Job，恢复实时链路。
