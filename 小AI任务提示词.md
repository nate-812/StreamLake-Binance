# StreamLake-Binance · 小AI任务提示词手册

> **使用说明**：当主AI（Cursor claude）不可用时，将对应 Phase 的提示词**完整复制**给其他AI（GPT-4o、DeepSeek、Doubao等），它们就能在正确的方向上继续开发。  
> 每个提示词都是自包含的，不需要额外上下文。  
> ⚠️ 严禁改变本文件指定的技术选型和版本号。

---

## 提示词索引

- [通用项目背景（所有提示词的前置背景）](#通用项目背景所有提示词必读)
- [Phase 1 提示词：Kafka KRaft 配置验证](#phase-1-提示词kafka-kraft-配置验证)
- [Phase 2 提示词：Python asyncio 采集器开发](#phase-2-提示词python-asyncio-采集器开发)
- [Phase 3A 提示词：Flink K 线聚合作业](#phase-3a-提示词flink-k-线聚合作业)
- [Phase 3B 提示词：Flink 巨鲸 CEP 作业](#phase-3b-提示词flink-巨鲸-cep-作业)
- [Phase 3C 提示词：Flink 风控广播作业](#phase-3c-提示词flink-风控广播作业)
- [Phase 4 提示词：Doris 建表与 Paimon 联邦查询](#phase-4-提示词doris-建表与-paimon-联邦查询)
- [Phase 5A 提示词：Spring Boot API 服务](#phase-5a-提示词spring-boot-api-服务)
- [Phase 5B 提示词：Python RAG AI Engine](#phase-5b-提示词python-rag-ai-engine)
- [Phase 6 提示词：React 19 前端大屏](#phase-6-提示词react-19-前端大屏)
- [Phase 7 提示词：Prometheus + Grafana 监控](#phase-7-提示词prometheus--grafana-监控)
- [排障提示词：常见问题诊断](#排障提示词常见问题诊断)

---

## 通用项目背景（所有提示词必读）

> 将下面这段背景复制到每个任务提示词的最前面。

```
【项目背景】
项目名：StreamLake-Binance（全球加密货币实时量化与风控大盘）
架构类型：Kappa 架构湖仓一体（Lakehouse）
数据源：Binance（币安）公开 WebSocket 接口，实时 tick 数据

【集群环境】
- 操作系统：Ubuntu 22.04 LTS
- 3 台阿里云 ECS（抢占式实例，内网 IP 通过 ENI 固定）：
  master-01：192.168.1.10（Kafka / Flink JobManager / MySQL / Redis / Doris FE / Spring Boot / Prometheus / Grafana）
  worker-01：192.168.1.20（Flink TaskManager / Doris BE）
  worker-02：192.168.1.30（Flink TaskManager / Doris BE / Milvus）
- 每台节点：8 vCPU / 32GB RAM / 80GB 系统盘（无独立数据盘）
- 阿里云 OSS Bucket：oss://streamlake-paimon-tokyo/（日本东京地域）

【已完成的基础设施】
- 三节点 SSH 互通，/etc/hosts 已配置 hostname → 固定 IP 映射
- JDK 21（Eclipse Temurin）已安装，JAVA_HOME 已配置
- Docker 已安装

【软件版本（严格遵守，不得擅自升级或降级）】
- Apache Kafka：3.8.x（KRaft 模式，无 ZooKeeper，安装在 /opt/kafka）
- Apache Flink：2.0.x（Standalone 模式，安装在 /opt/flink）
- Apache Paimon：1.1.x（以 JAR 依赖嵌入 Flink 作业，无独立进程）
- Apache Doris：3.0.x（Docker 部署）
- Redis：7.2.x
- MySQL：8.0.x
- Milvus：2.4.x（Docker 部署，worker-02）
- Python：3.12.x
  - websockets==12.0（WS 采集，asyncio 原生，不得使用 websocket-client）
  - aiokafka==0.11.0（Kafka 生产者，异步）
  - pydantic==2.7.0
  - langchain==0.2.x
  - pymilvus==2.4.x
- Java：JDK 21 / Maven 3.9.x / Spring Boot 3.3.x
- Node.js：22 LTS（前端构建，仅本地 MacBook）
- React：19.x / Ant Design 5.x / TradingView Lightweight Charts 4.x

【端口约定】
Kafka: 9092 | Flink UI: 8081 | Doris FE MySQL: 9030 | Doris FE HTTP: 8030
MySQL: 3306 | Redis: 6379 | Milvus: 19530 | Spring Boot: 8080 | Grafana: 3000
```

---

## Phase 1 提示词：Kafka KRaft 配置验证

```
【粘贴通用项目背景到此处】

【当前状态】
Kafka 3.8 已下载并解压到 /opt/kafka，尚未配置和启动。

【本次任务】
完成以下工作：

1. 生成完整的 /opt/kafka/config/kraft/server.properties 配置内容
   要求：
   - 使用 KRaft 模式（无 ZooKeeper）
   - 单节点部署在 master-01（192.168.1.10）
   - Kafka 日志目录：/opt/kafka/logs
   - 数据保留策略：保留 6 小时（21600000 ms），单 Topic 最大 3GB
   - 开启 lz4 压缩
   - 分区数：6，副本数：1

2. 给出完整的初始化和启动命令（包括生成 Cluster UUID、格式化存储、后台启动）

3. 给出创建以下 3 个 Topic 的命令，并说明每个 Topic 的用途：
   - binance.trade.raw（Trade 原始数据，6 分区）
   - binance.depth.raw（Depth 原始数据，6 分区）
   - streamlake.whale.alert（巨鲸告警输出，3 分区）

4. 给出验证 Kafka 正常运行的测试命令

【交付物】
- server.properties 完整内容
- 分步骤的 bash 命令（可直接复制执行）
- 验证成功的判断标准

【注意事项】
- 所有地址使用 ENI 固定 IP：192.168.1.10，不能使用 localhost 或 0.0.0.0
- 不要使用 ZooKeeper，这是 KRaft 模式
- 所有命令假设在 ubuntu 用户下执行
```

---

## Phase 2 提示词：Python asyncio 采集器开发

```
【粘贴通用项目背景到此处】

【当前状态】
- Kafka 已运行，Topic binance.trade.raw 和 binance.depth.raw 已创建
- master-01 已安装 Python 3.12
- 工作目录：/opt/collector/（需要你来创建项目结构）

【本次任务】
在 /opt/collector/ 下开发一个 Python asyncio WebSocket 采集器，
实时订阅 Binance 的 Trade 数据流并推送到 Kafka。

要求实现以下文件：
1. requirements.txt（使用 websockets==12.0，aiokafka==0.11.0，pydantic==2.7.0，pyyaml==6.0.1）

2. config.yaml
   - symbols 列表包含 Top-30 交易对（BTCUSDT, ETHUSDT, BNBUSDT, SOLUSDT, XRPUSDT, DOGEUSDT,
     ADAUSDT, AVAXUSDT, LINKUSDT, DOTUSDT, MATICUSDT, UNIUSDT, LTCUSDT, ATOMUSDT, XLMUSDT,
     ETCUSDT, ALGOUSDT, VETUSDT, FILUSDT, TRXUSDT, NEARUSDT, APTUSDT, ARBUSDT, OPUSDT,
     INJUSDT, SUIUSDT, SEIUSDT, TIAUSDT, WLDUSDT, JUPUSDT）
   - Kafka bootstrap_servers: 192.168.1.10:9092
   - 重连配置：初始等待 5 秒，最大等待 60 秒（指数退避）

3. binance/models.py
   - TradeEvent Pydantic 模型，字段：stream, symbol, event_time(int ms), trade_id, price(str), qty(str), is_buyer_maker(bool)
   - from_raw(cls, data: dict) 类方法，从 Binance 原始 JSON 解析

4. binance/client.py
   - BinanceWSClient 类，asyncio + websockets 库
   - 实现指数退避自动重连（ConnectionClosed 或任何异常都触发）
   - ping_interval=20 保活
   - 接受 on_message 异步回调函数

5. sink/kafka_sink.py
   - 基于 aiokafka.AIOKafkaProducer 的异步 Kafka 生产者封装
   - start() 和 stop() 生命周期方法
   - send(topic, value: bytes) 异步方法

6. main.py
   - asyncio 主入口
   - 读取 config.yaml
   - 按 Binance 限制（每连接最多 200 stream）自动分批，每批创建一个 BinanceWSClient
   - 每个 @trade 消息：序列化为 JSON bytes，发送到 binance.trade.raw
   - 全局异常处理，Ctrl+C 优雅退出

7. 给出 systemd service 配置，使采集器开机自启、崩溃自动重启

【交付物】
- 以上所有文件的完整代码
- 安装依赖的命令（使用 venv）
- 验证数据流入 Kafka 的命令

【注意事项】
- 绝对不能使用 websocket-client 库（同步阻塞，已废弃不用）
- 必须使用 websockets 库（asyncio 原生）
- price/qty 字段保持字符串类型，不要转 float（避免浮点精度丢失）
- Binance combined stream URL 格式：
  wss://stream.binance.com:9443/stream?streams=btcusdt@trade/ethusdt@trade/...
```

---

## Phase 3A 提示词：Flink K 线聚合作业

```
【粘贴通用项目背景到此处】

【当前状态】
- Kafka 运行正常，binance.trade.raw Topic 中有持续流入的 JSON 数据
- Flink 2.0 Standalone 集群已启动，JobManager 在 master-01:8081
- Doris 已运行，streamlake 库和 kline_1min 表已创建（UNIQUE KEY，字段见下方）
- Maven 项目骨架已建立：stream-jobs/（父 POM + common 模块已建好）
- common 模块已有 TradeEvent POJO（字段：symbol, eventTime, tradeId, price(BigDecimal), qty(BigDecimal), isBuyerMaker）

【Doris kline_1min 表结构】
symbol VARCHAR(20), open_time DATETIME, open DECIMAL(20,8), high DECIMAL(20,8),
low DECIMAL(20,8), close DECIMAL(20,8), volume DECIMAL(30,8), quote_volume DECIMAL(30,8),
trade_count INT, is_closed BOOLEAN
UNIQUE KEY(symbol, open_time)，DISTRIBUTED BY HASH(symbol) BUCKETS 8

【本次任务】
在 stream-jobs/job-kline/ 模块下实现 KlineAggregationJob.java，完成以下功能：

1. Source：从 Kafka binance.trade.raw 消费，JSON 反序列化为 TradeEvent
   - Kafka bootstrap.servers: 192.168.1.10:9092
   - Consumer group: flink-kline-group
   - 起始消费位置：LATEST（不消费历史）

2. 时间语义：
   - 使用事件时间（Event Time），eventTime 字段是毫秒时间戳
   - Watermark 策略：允许 5 秒乱序（forBoundedOutOfOrderness）
   - 迟到 5 秒以上的数据：写入 side output，tag 为 "late-data"，暂时忽略即可

3. 聚合逻辑：
   - keyBy(symbol)
   - TumblingEventTimeWindow，同时支持 1 分钟窗口（主要）
   - OHLCVAggregateFunction：
     open = 第一条 price，high = max，low = min，close = 最后一条，
     volume = sum(qty)，quote_volume = sum(price*qty)，tradeCount = count

4. Sink：
   - Sink-1：Doris Flink Connector，写入 kline_1min 表
     使用 DorisSink（flink-doris-connector 2.0），StreamLoad 方式，
     FE 地址：192.168.1.10:8030，DB：streamlake，Table：kline_1min
   - Sink-2：PaimonSink，写入 Paimon OSS 的 dwd.kline_1min 表
     Catalog 配置：warehouse=oss://streamlake-paimon-tokyo/warehouse，
     OSS endpoint: oss-ap-northeast-1-internal.aliyuncs.com，
     OSS 密钥从环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET 读取

5. Checkpoint 配置：
   - 间隔 30 秒，EXACTLY_ONCE，增量 Checkpoint
   - 存储：oss://streamlake-paimon-tokyo/flink-checkpoints

6. 给出 pom.xml（job-kline 模块）中需要的依赖

7. 给出编译和提交命令：mvn package → flink run 提交

【交付物】
- KlineAggregationJob.java 完整代码
- job-kline/pom.xml 完整内容
- flink run 提交命令

【注意事项】
- Flink 版本 2.0，Scala 2.12，Java 21
- BigDecimal 运算要指定 MathContext，避免精度异常
- Doris root 用户密码为空字符串
- 环境变量 OSS_ACCESS_KEY_ID 和 OSS_ACCESS_KEY_SECRET 在 Flink 启动前 export
```

---

## Phase 3B 提示词：Flink 巨鲸 CEP 作业

```
【粘贴通用项目背景到此处】

【当前状态】
- Kafka 运行正常，binance.trade.raw 有持续数据流
- Doris whale_alert 表已创建（DUPLICATE KEY，字段：alert_id VARCHAR(64), symbol VARCHAR(20), alert_time DATETIME, direction VARCHAR(4), total_quote DECIMAL(30,8), trigger_count INT, severity TINYINT）
- stream-jobs/common 模块已有 TradeEvent POJO 和 WhaleAlert POJO
- MySQL risk_rules 表已创建（字段：id INT, symbol VARCHAR(20), threshold_quote DECIMAL(20,8), time_window_sec INT, min_count INT, enabled BOOLEAN）

【本次任务】
在 stream-jobs/job-whale-cep/ 模块下实现 WhaleCepJob.java：

1. Source：从 Kafka binance.trade.raw 消费 TradeEvent（同 Phase 3A 配置）
   Consumer group: flink-cep-group

2. CEP 模式设计（重点）：
   检测目标：在 time_window_sec 秒内，同一交易对同方向（买/卖）的连续成交中，
   单笔成交额（price * qty）超过 threshold_quote 的次数 ≥ min_count
   触发后生成 WhaleAlert。

   实现思路：
   - keyBy(symbol)
   - 对每个 symbol 维护两个独立的 CEP pattern（BUY 方向 / SELL 方向）
   - Pattern 使用 timesOrMore().greedy().within()
   - 中间状态必须设置 State TTL = 2 * time_window_sec，防止状态无界增长

3. 告警严重程度（severity）判断：
   - total_quote < 2x threshold：severity=1（警告）
   - total_quote 2x~5x threshold：severity=2（严重）
   - total_quote > 5x threshold：severity=3（极端）

4. Sink：
   - Doris whale_alert 表（DorisSink，同 Phase 3A 配置）
   - Kafka Topic streamlake.whale.alert（供 Spring Boot WebSocket 实时推送前端）

5. 阈值来源：从 MySQL risk_rules 表读取
   - 使用 Flink CDC 的 MySQL Source 监听规则变更
   - 将规则变更流 broadcast 到所有并行算子（BroadcastState）
   - 初始加载：作业启动时读取全量规则

6. Checkpoint：与 Job-1 相同配置，但 group id 不同

【交付物】
- WhaleCepJob.java 完整代码
- job-whale-cep/pom.xml（需要 flink-cep、flink-cdc-mysql 依赖）
- MySQL risk_rules 表建表 SQL 和初始插入几条示例规则

【注意事项】
- CEP 状态必须设置 TTL，这是防止 OOM 的关键
- Flink CEP 库坐标：org.apache.flink:flink-cep:2.0.0
- isBuyerMaker=false 表示买方主动，即买单（BUY）；true 表示卖方主动（SELL）
- alert_id 用 UUID.randomUUID().toString() 生成
```

---

## Phase 3C 提示词：Flink 风控广播作业

```
【粘贴通用项目背景到此处】

【当前状态】
- Kafka 运行，binance.trade.raw 有数据
- MySQL risk_rules 表已有数据（同 Phase 3B）
- Redis 7.2 已运行在 master-01:6379
- Doris risk_trigger 表已创建（字段：trigger_id VARCHAR(64), symbol VARCHAR(20), rule_id INT, trigger_time DATETIME, price DECIMAL(20,8), qty DECIMAL(20,8)）
- stream-jobs/common 模块已有 TradeEvent POJO 和 RiskRule POJO

【RiskRule POJO 字段】
id(int), symbol(String), maxSingleQty(BigDecimal) -- 单笔最大数量阈值

【本次任务】
在 stream-jobs/job-risk-control/ 模块下实现 RiskControlJob.java：

1. 控制流（规则广播流）：
   - Source：Flink CDC MySQL Source，监听 risk_rules 表的 INSERT/UPDATE/DELETE
   - 全量初始化：作业启动时读取所有 enabled=true 的规则
   - 转为 BroadcastStream，MapStateDescriptor 的 key 为 symbol(String)，value 为 RiskRule

2. 数据流：
   - Source：Kafka binance.trade.raw，反序列化为 TradeEvent
   - Consumer group: flink-risk-group

3. 核心处理（KeyedBroadcastProcessFunction）：
   - processElement()：
     从 BroadcastState 获取当前 symbol 的规则
     如果 trade.qty > rule.maxSingleQty，触发风控
     触发动作：
       a. 向 Redis 写入黑名单：SET risk:blacklist:{symbol} 1 EX 86400
       b. 生成 RiskTriggerEvent，发送到 Doris risk_trigger 表
   - processBroadcastElement()：
     根据 CDC 事件类型（INSERT/UPDATE → 更新规则，DELETE → 删除规则）更新 BroadcastState
     打印日志：规则热更新成功

4. Redis Sink：
   - 使用 Jedis 或 Lettuce 客户端
   - Redis 地址：192.168.1.10:6379，无密码
   - 在 RichSinkFunction 的 open() 方法中初始化连接

5. Doris Sink：同前述配置，写 risk_trigger 表

【交付物】
- RiskControlJob.java 完整代码
- job-risk-control/pom.xml
- Redis Sink 实现类代码

【注意事项】
- Flink CDC MySQL Connector 版本与 Flink 2.0 兼容的版本（请确认 artifactId 和 version）
- BroadcastState 是非 keyedState，不支持 TTL，无需担心状态爆炸（规则数量有限）
- Redis 写操作失败不应中断主流处理，用 try-catch 吞掉异常并记录日志
```

---

## Phase 4 提示词：Doris 建表与 Paimon 联邦查询

```
【粘贴通用项目背景到此处】

【当前状态】
- Doris 3.0 已通过 Docker 启动，FE 在 master-01，BE 在 worker-01/worker-02
- 可以通过 MySQL 客户端连接：mysql -h 192.168.1.10 -P 9030 -u root
- Paimon 由 Flink 作业写入 oss://streamlake-paimon-tokyo/warehouse/ 路径
- OSS AccessKey 已配置（假设 AK_ID 和 AK_SECRET 已知）

【本次任务】
1. 给出以下所有 Doris 建表 DDL（完整可执行 SQL）：
   - streamlake.kline_1min（UNIQUE KEY，字段见实施规划）
   - streamlake.whale_alert（DUPLICATE KEY）
   - streamlake.risk_trigger（DUPLICATE KEY）
   - streamlake.ai_diagnosis（DUPLICATE KEY，含 TEXT 类型的 report_md 字段）
   所有表 replication_num=1，按 symbol HASH 分桶

2. 给出创建 Paimon External Catalog 的 SQL（连接 OSS 上的 Paimon 数据）

3. 给出以下 5 条验证 SQL：
   - 查询 kline_1min 最新 10 条数据
   - 查询 whale_alert 中 severity=3 的最近告警
   - 通过 External Catalog 查询 Paimon 原始 trade 数据，按 symbol 聚合 count
   - 联邦查询：同时关联 Doris kline_1min 和 Paimon trade_raw（以 symbol 关联）
   - 统计每个 symbol 今日巨鲸告警次数

4. 给出 Doris BE 内存配置调整命令（将 mem_limit 设置为 16GB，适合 32GB 节点）

5. 给出定期清理策略：
   - kline_1min 只保留最近 30 天数据（Doris dynamic partition 配置）
   - whale_alert 只保留最近 90 天

【交付物】
- 完整可执行的 SQL 脚本（按顺序）
- 建表完成后的验证步骤

【注意事项】
- Doris 3.0 的 UNIQUE KEY 表推荐开启 enable_unique_key_merge_on_write=true
- Paimon Catalog type 字段值为 "paimon"，不是 "iceberg" 或其他
- OSS endpoint 使用内网地址：oss-ap-northeast-1-internal.aliyuncs.com（节省流量费）
```

---

## Phase 5A 提示词：Spring Boot API 服务

```
【粘贴通用项目背景到此处】

【当前状态】
- Doris 中有数据：kline_1min、whale_alert 表
- Kafka Topic streamlake.whale.alert 中有巨鲸告警消息
- Python AI Engine（/diagnose 接口）运行在 192.168.1.10:8000
- api-server/ Maven 项目骨架已建立，pom.xml 存在

【本次任务】
开发 Spring Boot 3.3 + JDK 21 的数据服务，实现以下功能：

1. application.yml 配置
   - 开启 Virtual Threads（spring.threads.virtual.enabled=true）
   - Doris JDBC 连接（使用 MySQL 驱动）：192.168.1.10:9030
   - AI Engine HTTP 地址：http://192.168.1.10:8000

2. REST API（Controller + Service）：

   GET /api/kline/{symbol}
   参数：interval(default="1min"), limit(default=100), startTime(可选,毫秒时间戳)
   返回：JSON 数组，每个元素包含 symbol/openTime/open/high/low/close/volume/quoteVolume/tradeCount

   GET /api/whale/alerts
   参数：symbol(可选), severity(可选), limit(default=20), startTime(可选)
   返回：JSON 数组，whale_alert 表的数据

   GET /api/market/overview
   返回：Top-30 交易对的最新 close price + 24h 涨跌幅（从 doris kline_1min 计算）

   POST /api/ai/diagnose
   请求体：{"symbol": "BTCUSDT"}
   动作：HTTP POST 调用 Python AI Engine /diagnose 接口，返回 Markdown 报告字符串
   同时将报告存入 Doris ai_diagnosis 表

3. WebSocket 实时推送（Spring WebSocket）：
   端点：ws://master-01:8080/ws/realtime
   订阅格式：客户端发送 {"type":"subscribe","symbol":"BTCUSDT"}
   推送内容：
   - 每分钟推送新 K 线（从 Doris 轮询，周期 5 秒）
   - 收到 Kafka streamlake.whale.alert 消息时立即推送（用 @KafkaListener 监听）

4. 全局异常处理（@ControllerAdvice）：
   - 统一返回格式：{"code": 200, "msg": "ok", "data": ...}
   - 异常时返回：{"code": 500, "msg": "error message", "data": null}

5. 给出 JAR 构建和启动命令（nohup 后台运行）

【交付物】
- 所有 Java 文件的完整代码
- pom.xml（Spring Boot 3.3，含 JDBC、WebSocket、Kafka 依赖）
- 启动和验证命令

【注意事项】
- 使用 JDBC Template 查 Doris，不要用 JPA（Doris 不完全兼容 JPA）
- WebSocket 推送不要用轮询（setFixedRate），而是用 Kafka Consumer 事件驱动
- Doris JDBC URL：jdbc:mysql://192.168.1.10:9030/streamlake?useSSL=false&characterEncoding=utf8
- Doris 驱动用 com.mysql.cj.jdbc.Driver（MySQL 8 驱动与 Doris MySQL 协议兼容）
```

---

## Phase 5B 提示词：Python RAG AI Engine

```
【粘贴通用项目背景到此处】

【当前状态】
- Milvus 2.4 已在 worker-02（192.168.1.30:19530）运行
- Doris 中有 whale_alert 和 kline_1min 数据
- 火山引擎 API Key 已获取（VOLC_API_KEY 环境变量）
- ai-engine/ 目录已创建，为空

【本次任务】
开发 Python 3.12 RAG AI Engine，暴露 HTTP 接口供 Spring Boot 调用：

1. requirements.txt：
   fastapi==0.111.0, uvicorn==0.29.0, langchain==0.2.0, langchain-community==0.2.0,
   pymilvus==2.4.0, PyMySQL==1.1.0, requests==2.31.0, pydantic==2.7.0

2. Milvus Collection 设计（milvus_store.py）：
   Collection 名：whale_events
   字段：id(INT64 PK), event_text(VARCHAR 500), embedding(FLOAT_VECTOR 1536),
         symbol(VARCHAR 20), event_time(INT64), severity(INT8)
   索引：IVF_FLAT，metric_type=IP（内积，等价余弦相似度）

3. Embedding 模块（embedder.py）：
   - 调用火山引擎 Embedding API（或使用 sentence-transformers 本地模型）
   - 将 whale_alert 记录转为自然语言文本再 Embedding
   - 文本模板："{symbol} 在 {alert_time} 发生 {direction} 方向鲸鱼活动，累计成交额 {total_quote} USDT，触发 {trigger_count} 次，严重程度 {severity}"

4. 索引构建定时任务（scheduler/index_builder.py）：
   - 每 30 分钟从 Doris 查询最新未索引的 whale_alert 记录
   - Embedding → 存入 Milvus
   - 记录已索引的最大 alert_time（存本地文件，防止重复 Embedding）

5. RAG 诊断链（rag/chain.py）：
   接收 symbol 参数，执行以下步骤：
   a. 从 Doris 查当前 symbol 的最新 1 小时 K 线数据（近 60 条）
   b. 从 Doris 查当前 symbol 最近 24 小时的鲸鱼告警
   c. 将当前异动描述 Embedding，从 Milvus 检索最相似历史事件 Top-5
   d. 组装 Prompt：
      系统提示：你是专业的加密货币量化分析师，请用中文分析
      用户输入：当前市场数据 + 历史相似案例 + 分析要求
   e. 调用火山引擎 Doubao-pro-32k（或 DeepSeek）API
   f. 返回 Markdown 格式诊断报告

6. FastAPI 入口（main.py）：
   POST /diagnose，请求体：{"symbol": "BTCUSDT"}，返回：{"report": "Markdown 内容"}
   GET /health，返回：{"status": "ok"}

7. 给出启动命令和 systemd service 配置

【交付物】
- 所有 Python 文件完整代码
- requirements.txt
- 系统启动命令

【注意事项】
- Doris 连接用 PyMySQL，host=192.168.1.10 port=9030 user=root password="" db=streamlake
- Milvus 连接：host=192.168.1.30 port=19530
- 火山引擎 API 调用失败时，返回降级报告（"当前 AI 服务暂时不可用，请稍后重试"），不要让接口报 500
- 所有外部 IO 调用用 try-except 包裹，有完整的 logging
```

---

## Phase 6 提示词：React 19 前端大屏

```
【粘贴通用项目背景到此处】

【当前状态】
- Spring Boot API 运行在 master-01:8080
  可用接口：GET /api/kline/{symbol}、GET /api/whale/alerts、GET /api/market/overview
  WebSocket：ws://master-01-公网IP:8080/ws/realtime
  POST /api/ai/diagnose（返回 Markdown 报告字符串）
- 前端在本地 MacBook 上开发，构建后 dist/ 上传到 master-01 由 Nginx 服务

【本次任务】
用 React 19 + TypeScript 开发暗黑风格的金融实时大屏，要求：

1. 初始化项目（Vite + React 19 + TypeScript）
   npm create vite@latest frontend -- --template react-ts
   依赖安装：antd@5，lightweight-charts@4，echarts@5，echarts-for-react@3，zustand@4，react-markdown@9

2. 主题配置（theme/darkTheme.ts）：
   Ant Design 5 暗黑主题，背景色 #0d1117，强调色 #00d09e（涨）/#ef5350（跌）

3. 全局状态（store/marketStore.ts，Zustand）：
   - selectedSymbol: string（当前选中交易对，默认 BTCUSDT）
   - klineData: KlineBar[]（K 线数组）
   - whaleAlerts: WhaleAlert[]（告警列表）
   - overviewData: MarketOverviewItem[]（概览数据）

4. WebSocket Hook（hooks/useRealtimeWS.ts）：
   - 连接 ws://API_BASE_URL/ws/realtime
   - 订阅当前 symbol：发送 {"type":"subscribe","symbol":selectedSymbol}
   - 收到新 K 线消息：更新 klineData（替换或追加最新 bar）
   - 收到鲸鱼告警消息：prepend 到 whaleAlerts
   - 断线 5 秒后自动重连

5. TradingView K 线图（components/KlinePanel/TradingViewChart.tsx）：
   - 使用 lightweight-charts createChart()
   - 创建 CandlestickSeries，从 klineData 初始化
   - 实时更新：series.update(latestBar)
   - 显示 volume 柱状图（HistogramSeries）
   - 图表背景透明，配合暗黑主题

6. 巨鲸告警面板（components/WhaleAlertPanel/AlertFeed.tsx）：
   - 滚动列表，最新告警在顶部
   - severity=3 的告警行显示红色脉冲动画
   - 每条显示：交易对、方向（🟢买/🔴卖）、金额、时间

7. 市场概览热力图（components/MarketHeatmap/Heatmap.tsx）：
   - ECharts treemap，节点大小=成交量，颜色=24h 涨跌幅（绿涨红跌）

8. AI 诊断悬浮舱（components/AiDiagnosis/DiagnosisDrawer.tsx）：
   - Ant Design Drawer 组件，从右侧滑入
   - 点击"AI 诊断"按钮触发
   - Loading Skeleton 等待 → 渲染 Markdown（react-markdown）
   - 报告标题、分析内容、建议操作分区展示

9. 主布局（pages/Dashboard/index.tsx）：
   顶部：导航栏（项目名 + 当前 BTC 价格实时显示）
   左侧（35%）：市场热力图 + 交易对选择列表
   中间（40%）：K 线图（TradingView）+ 底部成交量图
   右侧（25%）：巨鲸告警面板 + "AI 诊断"按钮

10. 构建和部署：
    - vite.config.ts：配置 API 代理（开发时）
    - 给出 npm run build 命令
    - 给出上传 dist/ 到 master-01 并配置 Nginx 的命令

【交付物】
- 所有文件完整代码（包括 package.json, vite.config.ts, tsconfig.json）
- Nginx 配置（处理 SPA 路由 + 反代 /api 到 Spring Boot）

【注意事项】
- API_BASE_URL 从环境变量读取（import.meta.env.VITE_API_BASE_URL）
- 图表时间显示东京时区（UTC+9）
- 所有数字格式化：价格保留 2-8 位小数，金额用 K/M/B 缩写
- 移动端不需要适配，纯 PC 大屏
```

---

## Phase 7 提示词：Prometheus + Grafana 监控

```
【粘贴通用项目背景到此处】

【当前状态】
- Docker 已安装在所有节点
- 所有服务（Kafka、Flink、Doris、Spring Boot）均已运行
- Spring Boot 已引入 spring-boot-actuator 和 micrometer-registry-prometheus 依赖

【本次任务】
在 master-01 部署 Prometheus + Grafana，建立全链路监控：

1. 给出 /opt/monitoring/docker-compose.yml，包含：
   - Prometheus 2.51（network_mode: host，数据目录 /opt/monitoring/prom-data）
   - Grafana 10.4（network_mode: host，admin 密码: streamlake123，数据目录 /opt/monitoring/grafana-data）
   - Prometheus 启动参数：数据保留 7 天，最大 2GB

2. 给出完整的 prometheus.yml，配置以下 scrape_configs：
   - node-exporter（三台节点）：:9100
   - Flink metrics reporter（需要在 flink-conf.yaml 开启）：master-01:9249
   - Kafka JMX exporter（kafka-exporter 或 jmx_prometheus_javaagent）：master-01:9308
   - Spring Boot Actuator：master-01:8080/actuator/prometheus
   - Doris FE metrics：master-01:8030/metrics

3. 给出 Flink 开启 Prometheus Metrics Reporter 的配置（追加到 flink-conf.yaml）

4. 给出在 worker-01 和 worker-02 上安装并启动 node-exporter 的命令（Docker 方式）

5. 给出 Grafana 手动创建以下 4 个 Dashboard Panel 的 PromQL 表达式：
   - Kafka Consumer Lag（以 consumer group 分组）
   - Flink Checkpoint 最近耗时（毫秒）
   - 三节点内存使用率（%）
   - Spring Boot API HTTP 请求总量（按 status code 分组）

6. 给出设置 Grafana Alert Rule 的步骤（当 Kafka Lag > 10000 时发送告警）

【交付物】
- docker-compose.yml
- prometheus.yml
- flink-conf.yaml 追加内容
- 所有启动和验证命令
- 4 个 PromQL 表达式

【注意事项】
- 所有组件使用 network_mode: host，避免 Docker 网桥带来的端口映射问题
- Grafana 初始化后需要手动在 UI 添加 Prometheus 数据源（URL: http://localhost:9090）
- Flink Metrics Reporter 端口不要与已有端口冲突
```

---

## 排障提示词：常见问题诊断

```
【粘贴通用项目背景到此处】

【我遇到了以下问题】
（在这里描述你的具体问题和报错信息）

【当前状态】
- 已完成的 Phase：（列出）
- 出问题的组件：（如 Kafka / Flink / Doris / Python 采集器 / Spring Boot）
- 报错信息：（完整粘贴）
- 相关日志路径：
  Kafka: /opt/kafka/logs/
  Flink: /opt/flink/log/
  Python 采集器: journalctl -u streamlake-collector -n 100
  Doris: /opt/doris/fe-log/ 或 /opt/doris/be-log/
  Spring Boot: nohup.out 或 systemd journal

【请你帮我】
1. 分析报错原因（说明是什么问题，为什么会出现）
2. 给出解决步骤（可直接执行的命令）
3. 给出验证已解决的方法

【补充检查命令参考】
# 查看各组件状态
/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server 192.168.1.10:9092
curl http://192.168.1.10:8081/overview  # Flink JobManager
mysql -h 192.168.1.10 -P 9030 -u root -e "SHOW FRONTENDS"  # Doris
redis-cli -h 192.168.1.10 ping
# 查看端口占用
ss -tlnp | grep <端口号>
# 查看磁盘使用
df -h && du -sh /opt/*/
```

---

## 附录：抢占式实例被释放后的恢复流程

```
【情况说明】
抢占式实例被阿里云释放，实例消失了，需要重新购买并恢复服务。

【恢复步骤】
请告诉我需要恢复哪台节点（master-01/worker-01/worker-02），
然后按以下流程指导操作：

1. 购买同规格新实例（同 VPC + 交换机）
2. 控制台：弹性网卡 → 解绑旧实例 → 挂载到新实例
3. 新实例 OS 内配置 ENI（netplan apply）
4. 执行 /etc/hosts 配置（从 Phase 0 Step 0-3 复制）
5. 安装 JDK 21（SDKMAN）
6. 安装 Docker
7. 根据节点角色重启对应服务：
   master-01：Kafka → MySQL → Redis → Flink JM → Doris FE → Spring Boot → Prometheus/Grafana → Python 采集器
   worker-01/02：Flink TM → Doris BE（+ Milvus on worker-02）

注意：Flink 作业状态保存在 OSS（Checkpoint），重启后从最近 Checkpoint 自动恢复，
数据不会丢失（已消费的 Kafka offset 记录在 Flink State 中）。
```
