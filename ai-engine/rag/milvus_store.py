from __future__ import annotations

from dataclasses import dataclass
from typing import List


@dataclass
class CaseItem:
    symbol: str
    summary: str
    severity: int


def search_similar_cases(symbol: str, top_k: int = 5) -> List[CaseItem]:
    """
    占位检索：
    当前返回静态样例，后续可替换为真实 Milvus ANN 检索。
    """
    seeds = [
        CaseItem(symbol=symbol, summary="短时成交额放大，卖压集中释放", severity=3),
        CaseItem(symbol=symbol, summary="连续大单触发，波动率上升", severity=2),
        CaseItem(symbol=symbol, summary="方向性买单增强，量价齐升", severity=2),
        CaseItem(symbol=symbol, summary="盘口流动性收缩，滑点风险增大", severity=3),
        CaseItem(symbol=symbol, summary="情绪驱动放量，建议观察回撤", severity=1),
    ]
    return seeds[:top_k]
