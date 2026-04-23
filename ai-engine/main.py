import threading
from contextlib import asynccontextmanager
from typing import Any, List

from fastapi import FastAPI
from pydantic import BaseModel, Field

from rag.chain import build_report_markdown


class DiagnoseRequest(BaseModel):
    symbol: str = Field(..., min_length=1, description="交易对，例如 BTCUSDT")
    klines:       List[Any] = Field(default_factory=list, description="最近 N 根 1min K 线（时序升序）")
    whaleAlerts:  List[Any] = Field(default_factory=list, description="最近巨鲸告警列表")
    riskTriggers: List[Any] = Field(default_factory=list, description="最近风控触发列表")


class DiagnoseResponse(BaseModel):
    symbol: str
    reportMarkdown: str


# ── 应用生命周期：启动时初始化 Milvus + 开启后台索引线程 ──────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    _init_rag()
    yield


def _init_rag() -> None:
    """初始化 Milvus Collection，并在后台线程中启动定时索引。"""
    try:
        from rag.milvus_store import init_collection
        init_collection()

        from scheduler.index_builder import run_loop
        t = threading.Thread(
            target=run_loop,
            kwargs={"interval_seconds": 300},   # 每 5 分钟同步一次 Doris→Milvus
            daemon=True,
            name="rag-index-builder",
        )
        t.start()
        print("[Startup] RAG 初始化完成，后台索引线程已启动")
    except Exception as e:
        # Milvus 未就绪时降级运行，不影响 LLM 正常分析
        print(f"[Startup] Milvus 初始化失败（降级运行，RAG 不可用）: {e}")


# ── FastAPI 应用 ──────────────────────────────────────────────────────────────

app = FastAPI(
    title="StreamLake AI Engine",
    version="0.3.0",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/diagnose", response_model=DiagnoseResponse)
def diagnose(req: DiagnoseRequest) -> DiagnoseResponse:
    symbol = req.symbol.upper().strip()
    report = build_report_markdown(
        symbol=symbol,
        klines=req.klines,
        whale_alerts=req.whaleAlerts,
        risk_triggers=req.riskTriggers,
    )
    return DiagnoseResponse(symbol=symbol, reportMarkdown=report)
