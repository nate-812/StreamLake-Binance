from __future__ import annotations

import os
import time

import pymysql
import pymysql.cursors

from rag.embedder import encode_whale_alert
from rag.milvus_store import init_collection, upsert_cases

_DORIS_HOST     = os.getenv("DORIS_HOST",     "192.168.1.10")
_DORIS_PORT     = int(os.getenv("DORIS_PORT", "9030"))
_DORIS_USER     = os.getenv("DORIS_USER",     "root")
_DORIS_PASSWORD = os.getenv("DORIS_PASSWORD", "")
_DORIS_DB       = os.getenv("DORIS_DB",       "streamlake")

# 每次同步最新 N 条（Doris 写时合并，取最新足够覆盖增量）
_BATCH_LIMIT = 1000


def _fetch_alerts() -> list[dict]:
    conn = pymysql.connect(
        host=_DORIS_HOST,
        port=_DORIS_PORT,
        user=_DORIS_USER,
        password=_DORIS_PASSWORD,
        database=_DORIS_DB,
        connect_timeout=10,
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT alert_id, symbol, alert_time, direction,
                       total_quote, trigger_count, severity
                FROM whale_alert
                ORDER BY alert_time DESC
                LIMIT %s
                """,
                (_BATCH_LIMIT,),
            )
            return cur.fetchall()
    finally:
        conn.close()


def _to_summary(row: dict) -> str:
    direction = row.get("direction", "?")
    symbol    = row.get("symbol",    "?")
    total     = float(row.get("total_quote",   0) or 0)
    sev       = int(row.get("severity",        1) or 1)
    cnt       = int(row.get("trigger_count",   0) or 0)
    t         = str(row.get("alert_time",      ""))[:16]
    sev_label = {1: "警告", 2: "严重", 3: "极端"}.get(sev, str(sev))
    return (
        f"{symbol} {direction} 方向，成交额 {total:,.0f} USDT，"
        f"触发 {cnt} 笔，严重度 {sev_label}，时间 {t}"
    )


def run_once() -> int:
    """
    从 Doris whale_alert 表拉取最新告警，embedding 后 upsert 进 Milvus。
    主键为 alert_id，天然去重（重复执行安全）。
    返回本次写入条数。
    """
    alerts = _fetch_alerts()
    if not alerts:
        return 0

    rows = []
    for a in alerts:
        rows.append({
            "alert_id":  str(a["alert_id"]),
            "symbol":    str(a.get("symbol",    "?")).upper(),
            "direction": str(a.get("direction", "?")).upper(),
            "severity":  int(a.get("severity",  1) or 1),
            "summary":   _to_summary(a),
            "embedding": encode_whale_alert(a),   # snake_case 字段名，embedder 已兼容
        })

    return upsert_cases(rows)


def run_loop(interval_seconds: int = 300) -> None:
    """
    阻塞式同步循环（在后台线程中运行）。
    启动后立即执行一次，之后每 interval_seconds 秒同步一次。
    """
    print(f"[IndexBuilder] 启动，同步间隔 {interval_seconds}s")
    while True:
        try:
            n = run_once()
            print(f"[IndexBuilder] 同步完成，本次写入 {n} 条历史案例到 Milvus")
        except Exception as e:
            print(f"[IndexBuilder] 同步失败（下次再试）: {e}")
        time.sleep(interval_seconds)


if __name__ == "__main__":
    # 支持独立运行：python -m scheduler.index_builder
    init_collection()
    run_loop()
