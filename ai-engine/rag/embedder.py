from __future__ import annotations

import math
from datetime import datetime
from typing import Any, List

# ── 128 维向量布局 ─────────────────────────────────────────────────────────
# [0-19]   Symbol one-hot（前20个高频交易对）
# [20]     Direction：BUY=+1.0, SELL=-1.0, 其他=0.0
# [21]     Severity 归一化：(sev-1)/2 → 0/0.5/1.0
# [22]     TotalQuote log10 归一化：log10(quote)/9，上限 1.0
# [23]     TriggerCount 归一化：count/20，上限 1.0
# [24-25]  一天中的小时 sin/cos 编码
# [26-27]  星期几 sin/cos 编码
# [28-127] 零填充（预留后续特征扩展）

_DIM = 128

_SYMBOLS = [
    "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT",
    "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "DOTUSDT", "LINKUSDT",
    "MATICUSDT", "LTCUSDT", "ATOMUSDT", "UNIUSDT", "ETCUSDT",
    "XLMUSDT", "ALGOUSDT", "VETUSDT", "ICPUSDT", "FILUSDT",
]


def encode_whale_alert(alert: Any) -> List[float]:
    """
    把巨鲸告警编码为 128 维特征向量。
    同时支持 camelCase（服务层 JSON 响应）和 snake_case（Doris 直查结果）。
    """
    if hasattr(alert, "__dict__"):
        alert = vars(alert)

    vec = [0.0] * _DIM

    # Symbol one-hot
    symbol = str(alert.get("symbol") or "").upper()
    if symbol in _SYMBOLS:
        vec[_SYMBOLS.index(symbol)] = 1.0

    # Direction
    direction = str(alert.get("direction") or "").upper()
    if direction == "BUY":
        vec[20] = 1.0
    elif direction == "SELL":
        vec[20] = -1.0

    # Severity
    try:
        sev = int(alert.get("severity") or 1)
        vec[21] = (max(1, min(3, sev)) - 1) / 2.0
    except (TypeError, ValueError):
        pass

    # TotalQuote（camelCase 或 snake_case）
    raw_quote = alert.get("totalQuote") or alert.get("total_quote") or 0
    try:
        q = float(raw_quote)
        if q > 0:
            vec[22] = min(math.log10(q) / 9.0, 1.0)  # log10(1B) ≈ 9
    except (TypeError, ValueError):
        pass

    # TriggerCount
    raw_cnt = alert.get("triggerCount") or alert.get("trigger_count") or 0
    try:
        vec[23] = min(int(raw_cnt) / 20.0, 1.0)
    except (TypeError, ValueError):
        pass

    # 时间特征（小时 sin/cos，星期 sin/cos）
    time_val = alert.get("alertTime") or alert.get("alert_time") or ""
    time_str = time_val.isoformat() if hasattr(time_val, "isoformat") else str(time_val)
    try:
        dt = datetime.fromisoformat(time_str.replace(" ", "T"))
        vec[24] = math.sin(2 * math.pi * dt.hour / 24)
        vec[25] = math.cos(2 * math.pi * dt.hour / 24)
        vec[26] = math.sin(2 * math.pi * dt.weekday() / 7)
        vec[27] = math.cos(2 * math.pi * dt.weekday() / 7)
    except (ValueError, AttributeError):
        pass

    # L2 归一化（使 L2 距离等价于余弦相似度）
    norm = math.sqrt(sum(x * x for x in vec))
    if norm > 0:
        vec = [x / norm for x in vec]

    return vec


def mean_vector(vectors: List[List[float]]) -> List[float]:
    """多个向量取均值，用作多条告警的综合查询向量。"""
    if not vectors:
        return [0.0] * _DIM
    return [sum(v[i] for v in vectors) / len(vectors) for i in range(_DIM)]
