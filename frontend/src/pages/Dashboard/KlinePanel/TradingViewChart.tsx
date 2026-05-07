import { useEffect, useRef, useState } from 'react'
import {
  createChart,
  IChartApi,
  ISeriesApi,
  CandlestickData,
  HistogramData,
  LineData,
  Time,
} from 'lightweight-charts'
import { useMarketStore, KlineBar, ChartInterval } from '../../../store/marketStore'
import { UP_COLOR, DOWN_COLOR, BORDER, TEXT_SUB, TEXT_MAIN, ACCENT, BG_PAGE } from '../../../theme/darkTheme'

// ── Constants ──────────────────────────────────────────────────────────────

const INTERVALS: ChartInterval[] = ['1m', '3m', '5m', '15m', '1h']

const INTERVAL_MINUTES: Record<ChartInterval, number> = {
  '1m': 1, '3m': 3, '5m': 5, '15m': 15, '1h': 60,
}

const VISIBLE_COUNT: Record<ChartInterval, number> = {
  '1m': 240, '3m': 220, '5m': 200, '15m': 180, '1h': 120,
}

// MA line colors
const MA_CONFIG = [
  { period: 5,  color: '#F0B90B', label: 'MA5'  },
  { period: 10, color: '#A57AFF', label: 'MA10' },
  { period: 20, color: '#58B9FF', label: 'MA20' },
]

// ── Data transform helpers ─────────────────────────────────────────────────

function toChartBar(k: KlineBar): CandlestickData {
  return {
    time:  Math.floor(new Date(k.openTime).getTime() / 1000) as Time,
    open:  Number(k.open),
    high:  Number(k.high),
    low:   Number(k.low),
    close: Number(k.close),
  }
}

function toVolumeBar(k: KlineBar): HistogramData<Time> {
  return {
    time:  Math.floor(new Date(k.openTime).getTime() / 1000) as Time,
    value: Number(k.volume),
    color: Number(k.close) >= Number(k.open) ? `${UP_COLOR}55` : `${DOWN_COLOR}55`,
  }
}

function buildMA(bars: KlineBar[], period: number): LineData<Time>[] {
  const out: LineData<Time>[] = []
  if (bars.length < period) return out
  let sum = 0
  for (let i = 0; i < bars.length; i++) {
    sum += Number(bars[i].close)
    if (i >= period) sum -= Number(bars[i - period].close)
    if (i >= period - 1) {
      out.push({
        time:  Math.floor(new Date(bars[i].openTime).getTime() / 1000) as Time,
        value: sum / period,
      })
    }
  }
  return out
}

function dedupeAsc(bars: KlineBar[]): KlineBar[] {
  const map = new Map<string, KlineBar>()
  for (const b of bars) map.set(b.openTime, b)
  return [...map.values()].sort((a, b) => +new Date(a.openTime) - +new Date(b.openTime))
}

function aggregateBars(bars: KlineBar[], intervalMinutes: number): KlineBar[] {
  if (intervalMinutes <= 1) return bars
  const buckets = new Map<number, KlineBar[]>()
  for (const b of bars) {
    const ts  = new Date(b.openTime).getTime()
    const key = Math.floor(ts / (intervalMinutes * 60_000)) * intervalMinutes * 60_000
    const list = buckets.get(key) ?? []
    list.push(b)
    buckets.set(key, list)
  }
  return [...buckets.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([bucketMs, list]) => {
      const sorted = list.sort((x, y) => +new Date(x.openTime) - +new Date(y.openTime))
      return {
        symbol:      sorted[0].symbol,
        openTime:    new Date(bucketMs).toISOString(),
        open:        Number(sorted[0].open),
        high:        Math.max(...sorted.map((x) => Number(x.high))),
        low:         Math.min(...sorted.map((x) => Number(x.low))),
        close:       Number(sorted[sorted.length - 1].close),
        volume:      sorted.reduce((s, x) => s + Number(x.volume), 0),
        quoteVolume: sorted.reduce((s, x) => s + Number(x.quoteVolume), 0),
        tradeCount:  sorted.reduce((s, x) => s + Number(x.tradeCount), 0),
        isClosed:    1,
      }
    })
}

function fmtVol(n: number): string {
  if (n >= 1e6) return (n / 1e6).toFixed(2) + 'M'
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K'
  return n.toFixed(2)
}

// 计算末尾连续阳线/阴线数（≥2 才返回）
function computeStreak(bars: KlineBar[]): { dir: 'up' | 'down'; count: number } | null {
  if (bars.length < 2) return null
  const last = bars[bars.length - 1]
  const dir  = Number(last.close) >= Number(last.open) ? 'up' : 'down'
  let count  = 1
  for (let i = bars.length - 2; i >= 0; i--) {
    const isUp = Number(bars[i].close) >= Number(bars[i].open)
    if ((dir === 'up') === isUp) count++
    else break
  }
  return count >= 2 ? { dir, count } : null
}

// ── Component ──────────────────────────────────────────────────────────────

interface InfoBar {
  timeText:  string
  open:      number
  high:      number
  low:       number
  close:     number
  volume:    number
  changePct: number | null
}

export default function TradingViewChart() {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef     = useRef<IChartApi | null>(null)
  const candleRef    = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const volumeRef    = useRef<ISeriesApi<'Histogram'> | null>(null)
  const maRefs       = useRef<ISeriesApi<'Line'>[]>([])
  const visibleRef   = useRef<KlineBar[]>([])

  const [infoBar, setInfoBar] = useState<InfoBar | null>(null)
  const [streak,  setStreak]  = useState<{ dir: 'up' | 'down'; count: number } | null>(null)

  const klines           = useMarketStore((s) => s.klines)
  const symbol           = useMarketStore((s) => s.symbol)
  const interval         = useMarketStore((s) => s.interval)
  const setChartInterval = useMarketStore((s) => s.setInterval)

  // ── Chart initialization (once) ──────────────────────────────────────────
  useEffect(() => {
    if (!containerRef.current) return

    const chart = createChart(containerRef.current, {
      layout: {
        background: { color: BG_PAGE },
        textColor:  '#848E9C',
        fontSize:   11,
      },
      grid: {
        vertLines: { color: '#1A1D24' },
        horzLines: { color: '#1A1D24' },
      },
      crosshair: {
        mode: 1,
        vertLine: { color: '#3D4553', width: 1, style: 3, labelBackgroundColor: '#2B3139' },
        horzLine: { color: '#3D4553', width: 1, style: 3, labelBackgroundColor: '#2B3139' },
      },
      rightPriceScale: {
        borderColor:  '#2B3139',
        scaleMargins: { top: 0.06, bottom: 0.22 },
      },
      timeScale: {
        borderColor:    '#2B3139',
        timeVisible:    true,
        secondsVisible: false,
        rightOffset:    3,
        barSpacing:     10,
        minBarSpacing:  5,
      },
      width:  containerRef.current.clientWidth,
      height: containerRef.current.clientHeight,
    })

    const candle = chart.addCandlestickSeries({
      upColor:       UP_COLOR,
      downColor:     DOWN_COLOR,
      borderVisible: false,
      wickUpColor:   UP_COLOR,
      wickDownColor: DOWN_COLOR,
      priceFormat:   { type: 'price', precision: 2, minMove: 0.01 },
    })

    const volume = chart.addHistogramSeries({
      priceScaleId: '',
      priceFormat:  { type: 'volume' },
    })
    volume.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0.0 } })

    const mas = MA_CONFIG.map(({ color }) =>
      chart.addLineSeries({
        color,
        lineWidth:        1,
        priceLineVisible: false,
        lastValueVisible: false,
      })
    )

    chartRef.current  = chart
    candleRef.current = candle
    volumeRef.current = volume
    maRefs.current    = mas

    chart.subscribeCrosshairMove((param: any) => {
      if (!param?.time) return
      const ts  = Number(param.time)
      const bar = visibleRef.current.find(
        (k) => Math.floor(new Date(k.openTime).getTime() / 1000) === ts
      )
      if (!bar) return
      const idx       = visibleRef.current.indexOf(bar)
      const prevClose = idx > 0 ? Number(visibleRef.current[idx - 1].close) : null
      const changePct = prevClose ? ((Number(bar.close) - prevClose) / prevClose) * 100 : null
      setInfoBar({
        timeText:  bar.openTime.slice(0, 16).replace('T', ' '),
        open:      Number(bar.open),
        high:      Number(bar.high),
        low:       Number(bar.low),
        close:     Number(bar.close),
        volume:    Number(bar.volume),
        changePct,
      })
    })

    const ro = new ResizeObserver(([entry]) => {
      chart.resize(entry.contentRect.width, entry.contentRect.height)
    })
    ro.observe(containerRef.current)

    return () => { ro.disconnect(); chart.remove() }
  }, [])

  // ── Data update ──────────────────────────────────────────────────────────
  useEffect(() => {
    const c  = candleRef.current
    const v  = volumeRef.current
    const ms = maRefs.current
    if (!c || !v || ms.length < 3) return

    if (klines.length === 0) {
      visibleRef.current = []
      c.setData([]); v.setData([])
      ms.forEach((m) => m.setData([]))
      setInfoBar(null)
      setStreak(null)
      return
    }

    const base       = dedupeAsc(klines)
    const step       = INTERVAL_MINUTES[interval]
    const normalized = aggregateBars(base, step)
    const count      = VISIBLE_COUNT[interval]
    const visible    = normalized.slice(-count)
    if (visible.length === 0) return

    visibleRef.current = visible
    c.setData(visible.map(toChartBar))
    v.setData(visible.map(toVolumeBar))
    ms.forEach((m, i) => m.setData(buildMA(visible, MA_CONFIG[i].period)))

    const last = visible[visible.length - 1]
    if (last) {
      const prev      = visible.length > 1 ? Number(visible[visible.length - 2].close) : null
      const changePct = prev ? ((Number(last.close) - prev) / prev) * 100 : null
      setInfoBar({
        timeText:  last.openTime.slice(0, 16).replace('T', ' '),
        open:      Number(last.open),
        high:      Number(last.high),
        low:       Number(last.low),
        close:     Number(last.close),
        volume:    Number(last.volume),
        changePct,
      })
    }

    setStreak(computeStreak(visible))
    chartRef.current?.timeScale().fitContent()
  }, [symbol, interval, klines])

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <div style={{ display: 'flex', flexDirection: 'column', width: '100%', height: '100%' }}>

      {/* Toolbar */}
      <div
        style={{
          height:       40,
          display:      'flex',
          alignItems:   'center',
          padding:      '0 12px',
          gap:          2,
          borderBottom: `1px solid ${BORDER}`,
          flexShrink:   0,
          userSelect:   'none',
        }}
      >
        {/* Interval Buttons */}
        {INTERVALS.map((iv) => (
          <button
            key={iv}
            onClick={() => setChartInterval(iv)}
            style={{
              padding:      '4px 10px',
              fontSize:     12,
              fontWeight:   interval === iv ? 700 : 400,
              color:        interval === iv ? '#0B0E11' : TEXT_SUB,
              background:   interval === iv ? ACCENT : 'transparent',
              border:       'none',
              borderRadius: 3,
              cursor:       'pointer',
              lineHeight:   1.4,
              transition:   'background 0.12s, color 0.12s',
            }}
          >
            {iv}
          </button>
        ))}

        {/* Divider */}
        <div style={{ width: 1, height: 16, background: BORDER, margin: '0 10px', flexShrink: 0 }} />

        {/* MA Legend */}
        {MA_CONFIG.map(({ label, color }) => (
          <span
            key={label}
            style={{ display: 'flex', alignItems: 'center', gap: 5, marginRight: 10 }}
          >
            <svg width="18" height="2" style={{ display: 'block' }}>
              <line x1="0" y1="1" x2="18" y2="1" stroke={color} strokeWidth="1.5" />
            </svg>
            <span style={{ fontSize: 11, color, lineHeight: 1 }}>{label}</span>
          </span>
        ))}

        {/* Streak badge */}
        {streak && (
          <div
            style={{
              display:      'flex',
              alignItems:   'center',
              gap:          5,
              padding:      '2px 9px',
              borderRadius: 4,
              background:   streak.dir === 'up' ? `${UP_COLOR}14` : `${DOWN_COLOR}14`,
              border:       `1px solid ${streak.dir === 'up' ? UP_COLOR : DOWN_COLOR}30`,
              marginLeft:   8,
              flexShrink:   0,
            }}
          >
            <span style={{
              fontSize:   13,
              color:      streak.dir === 'up' ? UP_COLOR : DOWN_COLOR,
              lineHeight: 1,
            }}>
              {streak.dir === 'up' ? '↑' : '↓'}
            </span>
            <span style={{
              fontSize:   11,
              fontWeight: 600,
              color:      streak.dir === 'up' ? UP_COLOR : DOWN_COLOR,
              lineHeight: 1,
            }}>
              连{streak.dir === 'up' ? '阳' : '阴'}&nbsp;{streak.count}
            </span>
          </div>
        )}

        {/* OHLC Info + Symbol (right-aligned) */}
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 14, flexShrink: 0 }}>
          <span style={{ fontSize: 12, color: TEXT_SUB, fontWeight: 500 }}>
            {symbol} · {interval}
          </span>
          {infoBar && (
            <div
              style={{
                display:           'flex',
                gap:               10,
                fontSize:          11,
                fontVariantNumeric: 'tabular-nums',
                color:             TEXT_SUB,
              }}
            >
              <span>O <span style={{ color: TEXT_MAIN }}>{infoBar.open.toFixed(2)}</span></span>
              <span>H <span style={{ color: UP_COLOR }}>{infoBar.high.toFixed(2)}</span></span>
              <span>L <span style={{ color: DOWN_COLOR }}>{infoBar.low.toFixed(2)}</span></span>
              <span>C <span style={{ color: TEXT_MAIN }}>{infoBar.close.toFixed(2)}</span></span>
              {infoBar.changePct != null && (
                <span
                  style={{
                    color:      infoBar.changePct >= 0 ? UP_COLOR : DOWN_COLOR,
                    fontWeight: 600,
                  }}
                >
                  {infoBar.changePct >= 0 ? '+' : ''}
                  {infoBar.changePct.toFixed(2)}%
                </span>
              )}
              <span style={{ color: '#58B9FF' }}>Vol {fmtVol(infoBar.volume)}</span>
            </div>
          )}
        </div>
      </div>

      {/* Chart Container */}
      <div ref={containerRef} style={{ flex: 1, minHeight: 0 }} />
    </div>
  )
}
