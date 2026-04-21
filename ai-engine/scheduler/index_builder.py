from __future__ import annotations

import time


def run_once() -> None:
    # 占位任务：后续可改为从 Doris 拉增量告警并写入 Milvus
    print("[index_builder] placeholder run completed")


if __name__ == "__main__":
    while True:
        run_once()
        time.sleep(60)
