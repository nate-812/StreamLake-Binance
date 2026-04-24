# StreamLake API 接口文档

本文档覆盖当前已上线的服务层（`api-server`）与 AI 层（`ai-engine`）接口。

## 1. 基础信息

- 服务层 Base URL：`http://<host>:8080`
- AI 层 Base URL：`http://<host>:8000`
- WebSocket：`ws://<host>:8080/ws/realtime`
- 数据格式：`application/json`

---

## 2. 服务层（api-server）接口

### 2.1 健康检查

- 方法：`GET`
- 路径：`/actuator/health`
- 示例：

```bash
curl -sS "http://127.0.0.1:8080/actuator/health"
```

---

### 2.2 K 线查询

- 方法：`GET`
- 路径：`/api/kline/{symbol}`
- Query 参数：
  - `interval`：默认 `1min`（当前仅支持 `1min`）
  - `limit`：默认 `100`，范围会被限制在 `1~1000`
- 示例：

```bash
curl -sS "http://127.0.0.1:8080/api/kline/BTCUSDT?interval=1min&limit=5"
```

---

### 2.3 巨鲸告警查询

- 方法：`GET`
- 路径：`/api/whale/alerts`
- Query 参数：
  - `symbol`：可选，不传则查全量最新
  - `limit`：默认 `100`，范围 `1~1000`
- 示例：

```bash
curl -sS "http://127.0.0.1:8080/api/whale/alerts?symbol=BTCUSDT&limit=5"
```

---

### 2.4 风控触发查询

- 方法：`GET`
- 路径：`/api/risk/triggers`
- Query 参数：
  - `symbol`：可选
  - `limit`：默认 `100`，范围 `1~1000`
- 示例：

```bash
curl -sS "http://127.0.0.1:8080/api/risk/triggers?symbol=BTCUSDT&limit=5"
```

---

### 2.5 市场摘要

- 方法：`GET`
- 路径：`/api/market/summary/{symbol}`
- 示例：

```bash
curl -sS "http://127.0.0.1:8080/api/market/summary/BTCUSDT"
```

---

### 2.6 AI 诊断（服务层聚合入口）

- 方法：`POST`
- 路径：`/api/ai/diagnose`
- 说明：
  - 服务层会自动聚合 K 线、巨鲸、风控数据并调用 AI 层
  - 本次诊断结果会写入 Doris `ai_diagnosis` 表
- 请求体：

```json
{
  "symbol": "BTCUSDT"
}
```

- 响应体示例：

```json
{
  "diagnosisId": "6c63d923-8a84-4b5e-86a0-4e4609c8f5f0",
  "symbol": "BTCUSDT",
  "reportMarkdown": "# BTCUSDT 诊断报告\n..."
}
```

- 示例：

```bash
curl -sS -X POST "http://127.0.0.1:8080/api/ai/diagnose" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"BTCUSDT"}'
```

---

### 2.7 AI 诊断历史查询

- 方法：`GET`
- 路径：`/api/ai/diagnosis/history`
- Query 参数：
  - `symbol`：必填
  - `limit`：默认 `20`，范围 `1~200`
- 示例：

```bash
curl -sS "http://127.0.0.1:8080/api/ai/diagnosis/history?symbol=BTCUSDT&limit=5"
```

- 响应体示例：

```json
[
  {
    "diagnosisId": "6c63d923-8a84-4b5e-86a0-4e4609c8f5f0",
    "symbol": "BTCUSDT",
    "createTime": "2026-04-23T22:10:11",
    "reportMarkdown": "# BTCUSDT 诊断报告\n..."
  }
]
```

---

## 3. WebSocket 实时推送

### 3.1 实时通道

- 路径：`/ws/realtime`
- 协议：原生 WebSocket
- 说明：当前推送类型为巨鲸告警批次，若没有新增告警，连接后可能无输出（正常）。

- 示例：

```bash
websocat "ws://127.0.0.1:8080/ws/realtime"
```

- 消息示例：

```json
{
  "type": "whale.alert.batch",
  "count": 2,
  "items": [
    {
      "alertId": "xxx",
      "symbol": "BTCUSDT",
      "alertTime": "2026-04-23T21:00:00",
      "direction": "BUY",
      "totalQuote": 300000.00,
      "triggerCount": 3,
      "severity": 3
    }
  ]
}
```

---

## 4. AI 层（ai-engine）接口

### 4.1 健康检查

- 方法：`GET`
- 路径：`/health`
- 示例：

```bash
curl -sS "http://127.0.0.1:8000/health"
```

---

### 4.2 诊断接口（AI 原始入口）

- 方法：`POST`
- 路径：`/diagnose`
- 请求体字段：
  - `symbol`：必填
  - `klines`：可选数组
  - `whaleAlerts`：可选数组
  - `riskTriggers`：可选数组

- 示例：

```bash
curl -sS -X POST "http://127.0.0.1:8000/diagnose" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol":"BTCUSDT",
    "whaleAlerts":[{"symbol":"BTCUSDT","direction":"BUY","severity":3,"totalQuote":300000,"triggerCount":3,"alertTime":"2026-04-23T12:00:00"}],
    "klines":[],
    "riskTriggers":[]
  }'
```

---

## 5. 快速验收清单

```bash
curl -sS "http://127.0.0.1:8000/health"
curl -sS "http://127.0.0.1:8080/actuator/health"
curl -sS "http://127.0.0.1:8080/api/kline/BTCUSDT?interval=1min&limit=3"
curl -sS "http://127.0.0.1:8080/api/whale/alerts?symbol=BTCUSDT&limit=3"
curl -sS "http://127.0.0.1:8080/api/risk/triggers?symbol=BTCUSDT&limit=3"
curl -sS "http://127.0.0.1:8080/api/market/summary/BTCUSDT"
curl -sS -X POST "http://127.0.0.1:8080/api/ai/diagnose" -H "Content-Type: application/json" -d '{"symbol":"BTCUSDT"}'
curl -sS "http://127.0.0.1:8080/api/ai/diagnosis/history?symbol=BTCUSDT&limit=3"
```

