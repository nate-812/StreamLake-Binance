from __future__ import annotations

import os
from dataclasses import dataclass
from typing import List, Optional

from pymilvus import (
    Collection,
    CollectionSchema,
    DataType,
    FieldSchema,
    connections,
    utility,
)

_COLLECTION_NAME = "whale_alert_cases"
_DIM = 128  # 必须与 embedder._DIM 保持一致

_MILVUS_HOST = os.getenv("MILVUS_HOST", "192.168.1.10")
_MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")

_collection: Optional[Collection] = None


@dataclass
class CaseItem:
    symbol: str
    summary: str
    severity: int


# ── 连接与初始化 ─────────────────────────────────────────────────────────────

def _connect() -> None:
    connections.connect("default", host=_MILVUS_HOST, port=_MILVUS_PORT, timeout=10)


def init_collection() -> Collection:
    """连接 Milvus，不存在则建表建索引，存在则直接加载。"""
    global _collection
    _connect()

    if not utility.has_collection(_COLLECTION_NAME):
        fields = [
            FieldSchema("alert_id",  DataType.VARCHAR,      max_length=64,  is_primary=True, auto_id=False),
            FieldSchema("symbol",    DataType.VARCHAR,      max_length=20),
            FieldSchema("direction", DataType.VARCHAR,      max_length=4),
            FieldSchema("severity",  DataType.INT8),
            FieldSchema("summary",   DataType.VARCHAR,      max_length=512),
            FieldSchema("embedding", DataType.FLOAT_VECTOR, dim=_DIM),
        ]
        schema = CollectionSchema(fields, description="Historical whale alert cases for RAG retrieval")
        col = Collection(_COLLECTION_NAME, schema)
        col.create_index(
            field_name="embedding",
            index_params={"metric_type": "L2", "index_type": "IVF_FLAT", "params": {"nlist": 128}},
        )
        print(f"[Milvus] Collection '{_COLLECTION_NAME}' 创建完成")
    else:
        col = Collection(_COLLECTION_NAME)

    col.load()
    _collection = col
    print(f"[Milvus] Collection 已加载，当前共 {col.num_entities} 条历史案例")
    return col


# ── CRUD ────────────────────────────────────────────────────────────────────

def upsert_cases(rows: List[dict]) -> int:
    """
    批量写入（或覆盖）历史案例到 Milvus。
    rows 中每条 dict 须含：alert_id, symbol, direction, severity, summary, embedding
    """
    col = _get_or_init()
    if not rows:
        return 0

    data = [
        [r["alert_id"]  for r in rows],
        [r["symbol"]    for r in rows],
        [r["direction"] for r in rows],
        [int(r["severity"]) for r in rows],
        [r["summary"]   for r in rows],
        [r["embedding"] for r in rows],
    ]
    col.upsert(data)
    col.flush()
    return len(rows)


def search_similar_cases(query_vector: List[float], top_k: int = 5) -> List[CaseItem]:
    """ANN 检索最相似的历史巨鲸告警案例。"""
    col = _get_or_init()
    if col.num_entities == 0:
        return []

    results = col.search(
        data=[query_vector],
        anns_field="embedding",
        param={"metric_type": "L2", "params": {"nprobe": 10}},
        limit=top_k,
        output_fields=["symbol", "severity", "summary"],
    )

    items: List[CaseItem] = []
    for hits in results:
        for hit in hits:
            items.append(CaseItem(
                symbol=hit.entity.get("symbol", "?"),
                summary=hit.entity.get("summary", ""),
                severity=hit.entity.get("severity", 1),
            ))
    return items


# ── 内部工具 ─────────────────────────────────────────────────────────────────

def _get_or_init() -> Collection:
    global _collection
    if _collection is None:
        init_collection()
    return _collection
