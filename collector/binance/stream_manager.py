from __future__ import annotations


def binance_stream_path(symbol: str, stream_kind: str) -> str:
    s = symbol.lower()
    if stream_kind == "trade":
        return f"{s}@trade"
    if stream_kind == "depth100ms":
        return f"{s}@depth@100ms"
    raise ValueError(f"Unknown stream kind: {stream_kind!r}")


def build_all_stream_paths(symbols: list[str], stream_kinds: list[str]) -> list[str]:
    paths: list[str] = []
    for sym in symbols:
        for kind in stream_kinds:
            paths.append(binance_stream_path(sym, kind))
    return paths


def chunk_streams(paths: list[str], max_per_conn: int) -> list[list[str]]:
    return [paths[i : i + max_per_conn] for i in range(0, len(paths), max_per_conn)]
