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

export type ChartInterval = '1m' | '3m' | '5m' | '15m' | '30m' | '1h' | '4h'

interface MarketState {
  symbol:      string
  interval:    ChartInterval
  klines:      KlineBar[]
  alerts:      WhaleAlert[]
  summary:     MarketSummary | null
  wsConnected: boolean

  setSymbol:         (s: string) => void
  setInterval:       (i: ChartInterval) => void
  setKlines:         (bars: KlineBar[]) => void
  prependAlerts:     (items: WhaleAlert[]) => void
  setSummary:        (s: MarketSummary) => void
  updateLatestKline: (bar: KlineBar) => void
  setWsConnected:    (v: boolean) => void
}

export const useMarketStore = create<MarketState>((set) => ({
  symbol:      'BTCUSDT',
  interval:    '5m',
  klines:      [],
  alerts:      [],
  summary:     null,
  wsConnected: false,

  setSymbol:  (symbol) => set({ symbol, klines: [], alerts: [], summary: null }),
  setInterval: (interval) => set({ interval }),

  // API 返回 DESC，存储转 ASC
  setKlines: (bars) => set({ klines: [...bars].reverse() }),

  prependAlerts: (items) =>
    set((s) => ({ alerts: [...items, ...s.alerts].slice(0, 200) })),

  setSummary: (summary) => set({ summary }),

  setWsConnected: (wsConnected) => set({ wsConnected }),

  updateLatestKline: (bar) =>
    set((s) => {
      const list = [...s.klines]
      const last = list[list.length - 1]
      if (last && last.openTime === bar.openTime) {
        list[list.length - 1] = bar
      } else {
        list.push(bar)
        if (list.length > 3500) list.shift()
      }
      return { klines: list }
    }),
}))

export const SYMBOL_LIST = [
  'BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT',
  'ADAUSDT', 'DOGEUSDT', 'AVAXUSDT', 'DOTUSDT', 'LINKUSDT',
  'MATICUSDT', 'LTCUSDT', 'ATOMUSDT', 'UNIUSDT', 'ETCUSDT',
]
