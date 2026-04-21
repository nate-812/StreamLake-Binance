from fastapi import FastAPI
from pydantic import BaseModel, Field

from rag.chain import build_report_markdown


class DiagnoseRequest(BaseModel):
    symbol: str = Field(..., min_length=1, description="交易对，例如 BTCUSDT")


class DiagnoseResponse(BaseModel):
    symbol: str
    reportMarkdown: str


app = FastAPI(title="StreamLake AI Engine", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/diagnose", response_model=DiagnoseResponse)
def diagnose(req: DiagnoseRequest) -> DiagnoseResponse:
    symbol = req.symbol.upper().strip()
    report = build_report_markdown(symbol)
    return DiagnoseResponse(symbol=symbol, reportMarkdown=report)
