import { KlineBar, MarketSummary, WhaleAlert } from '../store/marketStore'

const BASE = '/api'

async function get<T>(url: string): Promise<T> {
  const res = await fetch(BASE + url)
  if (!res.ok) throw new Error(`${res.status} ${url}`)
  return res.json()
}

export const fetchKlines = (symbol: string, limit = 100) =>
  get<KlineBar[]>(`/kline/${symbol}?interval=1min&limit=${limit}`)

export const fetchAlerts = (symbol: string, limit = 50) =>
  get<WhaleAlert[]>(`/whale/alerts?symbol=${symbol}&limit=${limit}`)

export const fetchSummary = (symbol: string) =>
  get<MarketSummary>(`/market/summary/${symbol}`)

export async function fetchDiagnosis(symbol: string) {
  const res = await fetch(`${BASE}/ai/diagnose`, {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify({ symbol }),
  })
  if (!res.ok) throw new Error(`AI 诊断失败：${res.status}`)
  return res.json() as Promise<{ symbol: string; reportMarkdown: string }>
}

/** 并行拉取多个交易对行情摘要（热力图用），失败的跳过 */
export async function fetchMultiSummary(symbols: string[]): Promise<MarketSummary[]> {
  const results = await Promise.allSettled(symbols.map(fetchSummary))
  return results
    .filter((r): r is PromiseFulfilledResult<MarketSummary> => r.status === 'fulfilled')
    .map((r) => r.value)
}
