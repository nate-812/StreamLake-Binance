"""
Binance 历史 K 线回填脚本
用途：一次性从 Binance 公开 REST API 拉取过去 N 天的 1m K 线，写入 Doris kline_1min 表。
用法：python3 backfill_klines.py [--days 7] [--symbols BTCUSDT,ETHUSDT,...]

依赖：pip install requests pymysql
"""

import argparse
import time
import sys
import requests
import pymysql
from datetime import datetime, timezone

# ── 配置（按实际情况修改） ──────────────────────────────────────────────────
DORIS_HOST     = "192.168.1.10"
DORIS_PORT     = 9030
DORIS_USER     = "root"
DORIS_PASSWORD = ""
DORIS_DB       = "streamlake"

DEFAULT_SYMBOLS = [
    "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT",
    "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "DOTUSDT", "LINKUSDT",
]

BINANCE_BASE    = "https://api.binance.com"
KLINE_ENDPOINT  = "/api/v3/klines"
BARS_PER_REQ    = 1000          # Binance 单次最多返回 1000 根
REQ_INTERVAL_S  = 0.25          # 每次请求间隔 250ms，避免触发限速（weight=2，1min=1200）

INSERT_SQL = """
INSERT INTO kline_1min
  (symbol, open_time, open, high, low, close, volume, quote_volume, trade_count, is_closed)
VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 1)
"""

# ── 工具函数 ────────────────────────────────────────────────────────────────

def fetch_klines(symbol: str, start_ms: int, end_ms: int) -> list:
    """分页拉取 [start_ms, end_ms) 区间内的所有 1m K 线"""
    bars = []
    cursor = start_ms
    while cursor < end_ms:
        params = {
            "symbol":    symbol,
            "interval":  "1m",
            "startTime": cursor,
            "endTime":   end_ms,
            "limit":     BARS_PER_REQ,
        }
        resp = requests.get(BINANCE_BASE + KLINE_ENDPOINT, params=params, timeout=15)
        resp.raise_for_status()
        batch = resp.json()
        if not batch:
            break
        bars.extend(batch)
        cursor = batch[-1][0] + 60_000    # 下一页从最后一根的下一分钟开始
        if len(batch) < BARS_PER_REQ:
            break                          # 已取完
        time.sleep(REQ_INTERVAL_S)
    return bars


def insert_bars(conn, symbol: str, bars: list):
    """批量写入，每批 500 条"""
    BATCH = 500
    rows = []
    for b in bars:
        open_time   = datetime.fromtimestamp(b[0] / 1000, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
        rows.append((
            symbol, open_time,
            b[1], b[2], b[3], b[4],   # open high low close
            b[5], b[7], int(b[8]),     # volume quoteVolume tradeCount
        ))
    with conn.cursor() as cur:
        for i in range(0, len(rows), BATCH):
            cur.executemany(INSERT_SQL, rows[i:i + BATCH])
    conn.commit()


# ── 主程序 ──────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Binance K 线回填")
    parser.add_argument("--days",    type=int,   default=7,   help="回填多少天（默认 7）")
    parser.add_argument("--symbols", type=str,   default="",  help="逗号分隔的交易对，留空用默认列表")
    args = parser.parse_args()

    symbols = [s.strip().upper() for s in args.symbols.split(",") if s.strip()] or DEFAULT_SYMBOLS
    days    = args.days

    now_ms   = int(time.time() * 1000)
    start_ms = now_ms - days * 24 * 3600 * 1000

    print(f"回填区间: {datetime.fromtimestamp(start_ms/1000)} → 现在，共 {days} 天")
    print(f"交易对: {symbols}\n")

    conn = pymysql.connect(
        host=DORIS_HOST, port=DORIS_PORT,
        user=DORIS_USER, password=DORIS_PASSWORD,
        database=DORIS_DB, charset="utf8mb4",
        autocommit=False,
    )

    total = 0
    for sym in symbols:
        print(f"  [{sym}] 拉取中...", end="", flush=True)
        try:
            bars = fetch_klines(sym, start_ms, now_ms)
            insert_bars(conn, sym, bars)
            total += len(bars)
            print(f" {len(bars)} 根 ✓")
        except Exception as e:
            print(f" 失败: {e}", file=sys.stderr)

    conn.close()
    print(f"\n完成！共写入 {total} 根 K 线。")


if __name__ == "__main__":
    main()
