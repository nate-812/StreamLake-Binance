from __future__ import annotations

import os
from typing import Any, List

from openai import OpenAI

from rag.embedder import encode_whale_alert, mean_vector
from rag.milvus_store import CaseItem, search_similar_cases

# ── LLM 客户端（延迟初始化）────────────────────────────────────────────────
_client: OpenAI | None = None
_ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
_ARK_MODEL    = os.getenv("ARK_MODEL", "ep-20260423180107-p5q5z")


def _get_client() -> OpenAI:
    global _client
    if _client is None:
        _client = OpenAI(base_url=_ARK_BASE_URL, api_key=os.getenv("ARK_API_KEY", ""))
    return _client


# ── 数据格式化 ───────────────────────────────────────────────────────────────

def _fmt_klines(klines: List[Any]) -> str:
    if not klines:
        return "（无 K 线数据）"
    header = "时间            | 开盘         | 最高         | 最低         | 收盘         | 成交额(USDT)     | 笔数"
    rows = []
    for k in klines:
        t = str(k.get("openTime", ""))[:16].replace("T", " ")
        rows.append(
            f"{t} | {k.get('open','?'):>12} | {k.get('high','?'):>12} | "
            f"{k.get('low','?'):>12} | {k.get('close','?'):>12} | "
            f"{k.get('quoteVolume','?'):>16} | {k.get('tradeCount','?')}"
        )
    return "\n".join([header, "---"] + rows)


def _fmt_whales(whales: List[Any]) -> str:
    if not whales:
        return "（近期无巨鲸告警）"
    lines = []
    for w in whales:
        t   = str(w.get("alertTime", ""))[:16].replace("T", " ")
        sev = {1: "警告", 2: "严重", 3: "极端"}.get(w.get("severity", 1), "?")
        lines.append(
            f"- [{t}] **{w.get('direction','?')}** 方向，"
            f"成交额 {w.get('totalQuote','?')} USDT，"
            f"触发 {w.get('triggerCount','?')} 笔，严重度 {sev}"
        )
    return "\n".join(lines)


def _fmt_risks(risks: List[Any]) -> str:
    if not risks:
        return "（近期无风控触发）"
    return "\n".join(
        f"- [{str(r.get('triggerTime',''))[:16].replace('T',' ')}] "
        f"价格 {r.get('price','?')}，超限数量 {r.get('qty','?')}"
        for r in risks
    )


def _fmt_similar_cases(cases: List[CaseItem]) -> str:
    if not cases:
        return "（向量库尚无历史案例，初次启动后台同步完成后可用）"
    lines = []
    for i, c in enumerate(cases, 1):
        sev = {1: "警告", 2: "严重", 3: "极端"}.get(c.severity, str(c.severity))
        lines.append(f"{i}. {c.summary}（严重度：{sev}）")
    return "\n".join(lines)


# ── RAG：用当前巨鲸告警计算查询向量，检索最相似历史案例 ─────────────────────

def _rag_similar_cases(symbol: str, whale_alerts: List[Any]) -> List[CaseItem]:
    try:
        if whale_alerts:
            vecs = [encode_whale_alert(w) for w in whale_alerts[:5]]
            query_vec = mean_vector(vecs)
        else:
            # 无告警时，用 symbol 合成一个中性查询向量
            query_vec = encode_whale_alert({"symbol": symbol, "direction": "BUY", "severity": 1})
        return search_similar_cases(query_vec, top_k=5)
    except Exception as e:
        print(f"[RAG] 向量检索失败（降级跳过）: {e}")
        return []


# ── 核心入口 ─────────────────────────────────────────────────────────────────

def build_report_markdown(
    symbol: str,
    klines: List[Any] | None = None,
    whale_alerts: List[Any] | None = None,
    risk_triggers: List[Any] | None = None,
) -> str:
    klines        = klines        or []
    whale_alerts  = whale_alerts  or []
    risk_triggers = risk_triggers or []

    # 区间速览
    price_hint = ""
    if klines:
        try:
            first_open = float(klines[0].get("open", 0))
            last_close = float(klines[-1].get("close", 0))
            if first_open:
                pct   = (last_close - first_open) / first_open * 100
                arrow = "↑" if pct >= 0 else "↓"
                price_hint = (
                    f"\n> **区间速览**：{arrow}{abs(pct):.3f}%，"
                    f"开盘 {first_open}，最新收盘 {last_close}"
                )
        except (TypeError, ValueError):
            pass

    # RAG 历史相似案例检索
    similar_cases = _rag_similar_cases(symbol, whale_alerts)

    user_msg = f"""请对以下加密货币实时市场数据进行专业分析，输出 Markdown 诊断报告。

## 分析标的：`{symbol}`{price_hint}

### K 线（1 分钟，共 {len(klines)} 根，时序升序）
{_fmt_klines(klines)}

### 巨鲸告警（共 {len(whale_alerts)} 条）
{_fmt_whales(whale_alerts)}

### 风控触发记录（共 {len(risk_triggers)} 条）
{_fmt_risks(risk_triggers)}

### 历史相似案例（Milvus 向量检索 Top-5）
{_fmt_similar_cases(similar_cases)}

---
请严格按以下结构输出报告（Markdown，不要输出结构外的额外说明）：

# {symbol} 诊断报告

## 市场状态
（价格趋势、波动幅度、成交节奏）

## 主力动向
（基于巨鲸方向与成交额，判断多空力量对比）

## 历史参照
（结合历史相似案例，说明当前情况的历史规律）

## 风险评级
**评级：Low / Medium / High / Extreme**
（给出 1-2 句评级依据）

## 操作建议
- 建议1
- 建议2
- 建议3（可选）"""

    system_msg = (
        "你是一个专业的加密货币量化分析师，擅长从实时 K 线、大单活动、风控数据和历史案例中提炼市场信号。"
        "风格要求：简洁、专业、有数据支撑，不做无依据的猜测。"
        "直接输出 Markdown，不要在报告结构之外添加任何前缀或后缀。"
    )

    try:
        resp = _get_client().chat.completions.create(
            model=_ARK_MODEL,
            messages=[
                {"role": "system", "content": system_msg},
                {"role": "user",   "content": user_msg},
            ],
            temperature=0.3,
            max_tokens=1500,
        )
        return resp.choices[0].message.content
    except Exception as e:
        return _fallback_report(symbol, klines, whale_alerts, risk_triggers, similar_cases, str(e))


# ── 降级报告 ─────────────────────────────────────────────────────────────────

def _fallback_report(
    symbol: str,
    klines: List[Any],
    whale_alerts: List[Any],
    risk_triggers: List[Any],
    similar_cases: List[CaseItem],
    err: str,
) -> str:
    lines = [f"# {symbol} 诊断报告（降级）", "", f"> LLM 暂不可用：{err}", ""]

    if klines:
        first, last = klines[0], klines[-1]
        lines += [
            "## K 线摘要",
            f"- 区间：{first.get('openTime','')} ~ {last.get('openTime','')}",
            f"- 最新收盘：{last.get('close','')}",
            f"- 区间最高：{max(float(k.get('high', 0)) for k in klines)}",
            f"- 区间最低：{min(float(k.get('low', 0)) for k in klines if k.get('low'))}",
            "",
        ]

    if whale_alerts:
        lines.append(f"## 巨鲸告警（{len(whale_alerts)} 条）")
        for w in whale_alerts[:5]:
            lines.append(f"- {w.get('direction')} {w.get('totalQuote')} USDT，严重度 {w.get('severity')}")
        lines.append("")

    if similar_cases:
        lines.append("## 历史相似案例（向量检索）")
        for i, c in enumerate(similar_cases, 1):
            lines.append(f"{i}. {c.summary}")
        lines.append("")

    lines.append("---\n确认 ARK_API_KEY 有效后，将自动切换为模型生成报告。")
    return "\n".join(lines)
