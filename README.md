# StreamLake-Binance

全球加密货币实时量化与风控大屏，全链路从数据采集到前端展示。

## 架构

```
Binance WSS → Python Collector → Kafka KRaft → Flink 2.0 (3 Jobs)
                                              → Doris 3.0 (OLAP)
                                              → Spring Boot 3.3 (REST + WebSocket)
                                              → React 19 前端大屏
                                              → AI Engine (LangChain + Milvus)
```

## 模块

| 目录 | 技术栈 | 说明 |
|------|--------|------|
| `collector/` | Python asyncio + websockets + aiokafka | Binance Top-50 交易对实时采集，推入 Kafka |
| `stream-jobs/` | Apache Flink 2.0 + JDK 21 | 3 个作业：K 线聚合、巨鲸 CEP、风控广播 |
| `api-server/` | Spring Boot 3.3 + Virtual Threads | REST API + WebSocket 实时推送 |
| `ai-engine/` | Python FastAPI + LangChain + Milvus | RAG 向量检索 + LLM 诊断报告 |
| `frontend/` | React 19 + TypeScript + Vite | TradingView K 线、巨鲸告警、AI 诊断抽屉、热力图 |

## 功能

- **实时 K 线** — 1m/3m/5m/15m/30m/1h/4h 多周期，WebSocket 增量推送
- **巨鲸告警** — Flink CEP 检测 60s 内同方向累计大单，三级告警
- **风控广播** — MySQL CDC 热更新规则，Redis 黑名单秒级生效
- **AI 诊断** — Milvus 检索历史异动 + DeepSeek 生成诊断报告
- **价格呼吸灯** — 实时价格变动可视化
- **市场热力图** — Top-50 涨跌幅一览

## 本地开发

```bash
# 前端（Mac 本地，连服务器后端）
cd frontend && npm run dev          # WS 模式，连 data1:8080
cd frontend && npm run dev:no-ws    # 纯 REST 模式
cd frontend && npm run dev:local-api  # 本地后端

# API 服务
cd api-server && mvn spring-boot:run

# AI 引擎
cd ai-engine && python main.py
```

## 服务器部署

详见 `实施规划.md`，核心组件：

| 节点 | 角色 |
|------|------|
| data1 (192.168.1.10) | Kafka, Flink JM, MySQL, Redis, Doris FE, Spring Boot, AI Engine, Prometheus, Grafana |
| data2 (192.168.1.20) | Flink TM, Doris BE |
| data3 (192.168.1.30) | Flink TM, Doris BE, Milvus |

## 监控

Prometheus + Grafana 全链路可观测：Kafka Lag、Flink Checkpoint 耗时、节点资源、API 延迟。

---

*Built with Claude Code (Cursor AI)*
