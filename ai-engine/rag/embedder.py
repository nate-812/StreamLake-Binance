from __future__ import annotations

import hashlib
from typing import List


def simple_text_embedding(text: str, dims: int = 16) -> List[float]:
    """
    占位 embedding：把文本哈希后映射为固定维度向量。
    方便 Phase 5 联调，后续可替换为真实 Embedding 模型。
    """
    digest = hashlib.sha256(text.encode("utf-8")).digest()
    values = []
    for i in range(dims):
        values.append((digest[i] / 255.0) * 2.0 - 1.0)
    return values
