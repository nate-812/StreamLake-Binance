from __future__ import annotations

import os
from typing import Any, List

from openai import OpenAI

# ── LLM 客户端（延迟初始化，避免启动时因环境变量未就绪而报错）─────────────────
_client: OpenAI | None = None

_ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
_ARK_MODEL = os.getenv("ARK_MODEL", "ep-20260423180107-p5q5z")


def _get_client() -> OpenAI:
    global _client
    if _client is None:
        _client = OpenAI(
            base_url=_ARK_BASE_URL,
            api_key=os.getenv("ARK_API_KEY", ""),
        )
    return _client


# ── 数据格式化：把 JSON 列表转成 LLM 易读的文本 ──────────────────────────────

def _fmt_klines(klines: List[Any]) -> str:
    if not klines:
        return "（无 K 线数据）"
    header = "时间            | 开盘         | 最高         | 最低         | 收盘         | 成交额(USDT)     | 笔数"
    sep    = "---"
    rows = []
    for k in klines:
        t = str(k.get("openTime", ""))[:16].replace("T", " ")
        rows.append(
            f"{t} | {k.get('open','?'):>12} | {k.get('high','?'):>12} | "
            f"{k.get('low','?'):>12} | {k.get('close','?'):>12} | "
            f"{k.get('quoteVolume','?'):>16} | {k.get('tradeCount','?')}"
        )
    return "\n".join([header, sep] + rows)


def _fmt_whales(whales: List[Any]) -> str:
    if not whales:
        return "（近期无巨鲸告警）"
    lines = []
    for w in whales:
        t = str(w.get("alertTime", ""))[:16].replace("T", " ")
        direction = w.get("direction", "?")
        total = w.get("totalQuote", 0)
        cnt = w.get("triggerCount", 0)
        sev = w.get("severity", 1)
        sev_label = {1: "警告", 2: "严重", 3: "极端"}.get(sev, str(sev))
        lines.append(
            f"- [{t}] **{direction}** 方向，成交额 {total} USDT，"
            f"触发 {cnt} 笔，严重度 {sev_label}"
        )
    return "\n".join(lines)


def _fmt_risks(risks: List[Any]) -> str:
    if not risks:
        return "（近期无风控触发）"
    lines = []
    for r in risks:
        t = str(r.get("triggerTime", ""))[:16].replace("T", " ")
        lines.append(
            f"- [{t}] 价格 {r.get('price','?')}，超限数量 {r.get('qty','?')}"
        )
    return "\n".join(lines)


# ── 核心：构建 Prompt，调用 LLM，返回 Markdown 报告 ──────────────────────────

def build_report_markdown(
    symbol: str,
    klines: List[Any] | None = None,
    whale_alerts: List[Any] | None = None,
    risk_triggers: List[Any] | None = None,
) -> str:
    klines = klines or []
    whale_alerts = whale_alerts or []
    risk_triggers = risk_triggers or []

    # 快速计算区间涨跌，作为 prompt 补充
    price_hint = ""
    if klines:
        try:
            first_open = float(klines[0].get("open", 0))
            last_close = float(klines[-1].get("close", 0))
            if first_open:
                pct = (last_close - first_open) / first_open * 100
                arrow = "↑" if pct >= 0 else "↓"
                price_hint = (
                    f"\n> **区间速览**：{arrow}{abs(pct):.3f}%，"
                    f"开盘 {first_open}，最新收盘 {last_close}"
                )
        except (TypeError, ValueError):
            pass

    user_msg = f"""请对以下加密货币实时市场数据进行专业分析，输出 Markdown 诊断报告。

## 分析标的：`{symbol}`{price_hint}

### K 线（1 分钟，共 {len(klines)} 根，时序升序）
{_fmt_klines(klines)}

### 巨鲸告警（大单活动，共 {len(whale_alerts)} 条）
{_fmt_whales(whale_alerts)}

### 风控触发记录（共 {len(risk_triggers)} 条）
{_fmt_risks(risk_triggers)}

---
请严格按以下结构输出报告（Markdown 格式，不要输出结构外的额外说明）：

# {symbol} 诊断报告

## 市场状态
（价格趋势、波动幅度、成交节奏）

## 主力动向
（基于巨鲸方向与成交额，判断多空力量对比）

## 风险评级
**评级：Low / Medium / High / Extreme**
（给出1-2句评级依据）

## 操作建议
- 建议1
- 建议2
- 建议3（可选）"""

    system_msg = (
        "你是一个专业的加密货币量化分析师，擅长从实时 K 线、大单活动和风控数据中提炼市场信号。"
        "风格要求：简洁、专业、有数据支撑，不做无依据的猜测。"
        "直接输出 Markdown，不要在报告结构之外添加任何前缀或后缀说明。"
    )

    try:
        resp = _get_client().chat.completions.create(
            model=_ARK_MODEL,
            messages=[
                {"role": "system", "content": system_msg},
                {"role": "user", "content": user_msg},
            ],
            temperature=0.3,
            max_tokens=1200,
        )
        return resp.choices[0].message.content
    except Exception as e:
        return _fallback_report(symbol, klines, whale_alerts, risk_triggers, str(e))


# ── 降级报告：LLM 不可用时，用本地数据拼出可读摘要 ──────────────────────────

def _fallback_report(
    symbol: str,
    klines: List[Any],
    whale_alerts: List[Any],
    risk_triggers: List[Any],
    err: str,
) -> str:
    lines = [f"# {symbol} 诊断报告（降级）", "", f"> LLM 暂不可用（{err}）", ""]

    if klines:
        first = klines[0]
        last = klines[-1]
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
            lines.append(
                f"- {w.get('direction')} {w.get('totalQuote')} USDT，严重度 {w.get('severity')}"
            )
        lines.append("")

    if risk_triggers:
        lines.append(f"## 风控触发（{len(risk_triggers)} 条）")
        for r in risk_triggers[:5]:
            lines.append(f"- 价格 {r.get('price')}，数量 {r.get('qty')}")
        lines.append("")

    lines.append("---\n启动 ai-engine 并确认 ARK_API_KEY 环境变量后，将自动切换为模型生成报告。")
    return "\n".join(lines)
