from typing import Any, List

from fastapi import FastAPI
from pydantic import BaseModel, Field

from rag.chain import build_report_markdown


class DiagnoseRequest(BaseModel):
    symbol: str = Field(..., min_length=1, description="交易对，例如 BTCUSDT")
    # 服务层聚合后传入的三类上下文数据（均为可选，缺失时退化为只凭 symbol 分析）
    klines: List[Any] = Field(default_factory=list, description="最近 N 根 1min K 线（时序升序）")
    whaleAlerts: List[Any] = Field(default_factory=list, description="最近巨鲸告警列表")
    riskTriggers: List[Any] = Field(default_factory=list, description="最近风控触发列表")


class DiagnoseResponse(BaseModel):
    symbol: str
    reportMarkdown: str


app = FastAPI(title="StreamLake AI Engine", version="0.2.0")


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
