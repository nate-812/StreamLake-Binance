# ai-engine

Phase 5 的 AI 诊断服务（FastAPI）。

## 功能

- `GET /health`：健康检查
- `POST /diagnose`：根据 `symbol` 返回 Markdown 诊断报告（当前为占位 RAG）

## 本地启动

```bash
cd ai-engine
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

## 联调

`api-server` 中设置：

```bash
export AI_ENGINE_BASE_URL=http://127.0.0.1:8000
```

然后调用：

```bash
curl -sS -X POST "http://127.0.0.1:8000/diagnose" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"BTCUSDT"}'
```
