# Stream Jobs (Phase 3A)

当前已实现：
- `common`：`TradeEvent` / `KlineBar` / `TradeEventDeserializer`
- `job-kline`：Kafka `binance.trade.raw` -> 1min OHLCV -> Doris `kline_1min`

## 构建

```bash
cd stream-jobs
mvn -q -DskipTests package
```

生成产物：
- `job-kline/target/job-kline-1.0.0-SNAPSHOT.jar`

## 运行前环境变量（可选，未设置走默认值）

- `KAFKA_BOOTSTRAP` 默认 `192.168.1.10:9092`
- `KAFKA_TOPIC` 默认 `binance.trade.raw`
- `KAFKA_GROUP` 默认 `flink-kline-group`
- `DORIS_JDBC_URL` 默认 `jdbc:mysql://192.168.1.10:9030/streamlake?useSSL=false`
- `DORIS_USER` 默认 `root`
- `DORIS_PASSWORD` 默认空

## 提交作业

```bash
/opt/flink/bin/flink run -d \
  -c com.streamlake.kline.KlineAggregationJob \
  /path/to/job-kline-1.0.0-SNAPSHOT.jar
```
