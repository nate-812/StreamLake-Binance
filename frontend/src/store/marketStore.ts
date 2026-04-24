import { create } from 'zustand'

export interface KlineBar {
  symbol:      string
  openTime:    string
  open:        number
  high:        number
  low:         number
  close:       number
  volume:      number
  quoteVolume: number
  tradeCount:  number
  isClosed?:   number
}

export interface WhaleAlert {
  alertId:      string
  symbol:       string
  alertTime:    string
  direction:    'BUY' | 'SELL'
  totalQuote:   number
  triggerCount: number
  severity:     1 | 2 | 3
}

export interface MarketSummary {
  symbol:              string
  lastPrice:           number | null
  priceChange24h:      number
  priceChangePct24h:   number
  volume24h:           number
  quoteVolume24h:      number
  whaleAlertCount1h:   number
  riskTriggerCount24h: number
}

interface MarketState {
  symbol:      string
  klines:      KlineBar[]
  alerts:      WhaleAlert[]
  summary:     MarketSummary | null

  setSymbol:         (s: string) => void
  setKlines:         (bars: KlineBar[]) => void      // 传入 DESC 数组，内部转 ASC
  prependAlerts:     (items: WhaleAlert[]) => void
  setSummary:        (s: MarketSummary) => void
  updateLatestKline: (bar: KlineBar) => void
}

export const useMarketStore = create<MarketState>((set) => ({
  symbol:  'BTCUSDT',
  klines:  [],
  alerts:  [],
  summary: null,

  setSymbol: (symbol) => set({ symbol, klines: [], alerts: [], summary: null }),

  // API 返回 DESC，存储 ASC（图表直接使用）
  setKlines: (bars) => set({ klines: [...bars].reverse() }),

  prependAlerts: (items) =>
    set((s) => ({ alerts: [...items, ...s.alerts].slice(0, 200) })),

  setSummary: (summary) => set({ summary }),

  updateLatestKline: (bar) =>
    set((s) => {
      const list = [...s.klines]
      const last = list[list.length - 1]
      if (last && last.openTime === bar.openTime) {
        list[list.length - 1] = bar          // 更新当前未闭合 K 线
      } else {
        list.push(bar)                        // 新的一根 K 线
        if (list.length > 300) list.shift()
      }
      return { klines: list }
    }),
}))

export const SYMBOL_LIST = [
  'BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT',
  'ADAUSDT', 'DOGEUSDT', 'AVAXUSDT', 'DOTUSDT', 'LINKUSDT',
  'MATICUSDT', 'LTCUSDT', 'ATOMUSDT', 'UNIUSDT', 'ETCUSDT',
]
