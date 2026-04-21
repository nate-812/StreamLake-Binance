from __future__ import annotations

from rag.milvus_store import search_similar_cases


def build_report_markdown(symbol: str) -> str:
    cases = search_similar_cases(symbol, top_k=5)
    case_lines = "\n".join(
        [f"- 案例{i + 1}：{c.summary}（severity={c.severity}）" for i, c in enumerate(cases)]
    )

    return f"""# AI 诊断报告

## 交易对
`{symbol}`

## 诊断结论
当前市场出现短时异动特征，建议结合最近 5 分钟告警密度、方向占比和成交额放大量进行人工复核。

## 相似历史案例（Top-5）
{case_lines}

## 操作建议
- 关注同方向大单是否连续出现（>=3 笔/60s）。
- 结合 K 线波动率与成交量变化，判断是否为趋势行情。
- 对高 severity 告警提高风控阈值与仓位审慎等级。
"""
