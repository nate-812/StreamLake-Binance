-- 来源：实施规划.md Step 4-3 / Step 4-4
-- 用途：集中记录 Doris 建库建表 + Paimon Catalog 注册与验证语句

-- 连接 Doris（MySQL 协议）：
-- mysql -h 192.168.1.10 -P 9030 -u root

CREATE DATABASE IF NOT EXISTS streamlake;
USE streamlake;

-- 1. 实时 1 分钟 K 线（UNIQUE KEY，写时合并）
CREATE TABLE kline_1min (
    symbol       VARCHAR(20)    NOT NULL,
    open_time    DATETIME       NOT NULL,
    open         DECIMAL(20,8)  NOT NULL,
    high         DECIMAL(20,8)  NOT NULL,
    low          DECIMAL(20,8)  NOT NULL,
    close        DECIMAL(20,8)  NOT NULL,
    volume       DECIMAL(30,8)  NOT NULL,
    quote_volume DECIMAL(30,8)  NOT NULL,
    trade_count  INT            NOT NULL,
    is_closed    TINYINT        NOT NULL DEFAULT 0
) UNIQUE KEY(symbol, open_time)
DISTRIBUTED BY HASH(symbol) BUCKETS 8
PROPERTIES (
    "replication_num" = "1",
    "enable_unique_key_merge_on_write" = "true"
);

-- 2. 巨鲸告警（DUPLICATE KEY，保留全量）
CREATE TABLE whale_alert (
    alert_id      VARCHAR(64)    NOT NULL,
    symbol        VARCHAR(20)    NOT NULL,
    alert_time    DATETIME       NOT NULL,
    direction     VARCHAR(4)     NOT NULL,  -- BUY/SELL
    total_quote   DECIMAL(30,8)  NOT NULL,
    trigger_count INT            NOT NULL,
    severity      TINYINT        NOT NULL   -- 1=警告 2=严重 3=极端
) DUPLICATE KEY(alert_id)
DISTRIBUTED BY HASH(symbol) BUCKETS 4
PROPERTIES (
    "replication_num" = "2"
);

-- 3. 风控触发记录
CREATE TABLE risk_trigger (
    trigger_id  VARCHAR(64)   NOT NULL,
    symbol      VARCHAR(20)   NOT NULL,
    rule_id     INT           NOT NULL,
    trigger_time DATETIME     NOT NULL,
    price       DECIMAL(20,8) NOT NULL,
    qty         DECIMAL(20,8) NOT NULL
) DUPLICATE KEY(trigger_id)
DISTRIBUTED BY HASH(symbol) BUCKETS 4
PROPERTIES (
    "replication_num" = "2"
);

-- 4. AI 诊断报告（JSON 存储）
CREATE TABLE ai_diagnosis (
    diagnosis_id  VARCHAR(64) NOT NULL,
    symbol        VARCHAR(20) NOT NULL,
    create_time   DATETIME    NOT NULL,
    report_md     TEXT        NOT NULL  -- Markdown 格式报告
) DUPLICATE KEY(diagnosis_id)
DISTRIBUTED BY HASH(symbol) BUCKETS 4
PROPERTIES (
    "replication_num" = "2"
);

-- 在 Doris 中注册 Paimon Catalog
CREATE CATALOG paimon_oss PROPERTIES (
    "type" = "paimon",
    "warehouse" = "oss://streamlake-paimon-tokyo/warehouse",
    "oss.endpoint" = "oss-ap-northeast-1-internal.aliyuncs.com",
    "oss.access_key" = "<YOUR_ACCESS_KEY>",
    "oss.secret_key" = "<YOUR_SECRET_KEY>"
);

-- 验证联邦查询
SELECT symbol, COUNT(*) cnt
FROM paimon_oss.ods.trade_raw
WHERE event_time >= NOW() - INTERVAL 1 HOUR
GROUP BY symbol
ORDER BY cnt DESC
LIMIT 10;
