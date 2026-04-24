import { useEffect, useRef } from 'react'
import { createChart, IChartApi, ISeriesApi, CandlestickData, Time } from 'lightweight-charts'
import { useMarketStore, KlineBar } from '../../../store/marketStore'

function toChartBar(k: KlineBar): CandlestickData {
  return {
    time:  Math.floor(new Date(k.openTime).getTime() / 1000) as Time,
    open:  Number(k.open),
    high:  Number(k.high),
    low:   Number(k.low),
    close: Number(k.close),
  }
}

function dedupeByOpenTimeAsc(bars: KlineBar[]): KlineBar[] {
  const map = new Map<string, KlineBar>()
  for (const b of bars) map.set(b.openTime, b)
  return [...map.values()].sort((a, b) => +new Date(a.openTime) - +new Date(b.openTime))
}

export default function TradingViewChart() {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef     = useRef<IChartApi | null>(null)
  const seriesRef    = useRef<ISeriesApi<'Candlestick'> | null>(null)

  const klines = useMarketStore((s) => s.klines)
  const symbol = useMarketStore((s) => s.symbol)

  // ── 初始化图表（仅一次）────────────────────────────────────────────────
  useEffect(() => {
    if (!containerRef.current) return

    const chart = createChart(containerRef.current, {
      layout: {
        background: { color: '#161b22' },
        textColor:  '#8b949e',
      },
      grid: {
        vertLines: { color: '#21262d' },
        horzLines: { color: '#21262d' },
      },
      crosshair: {
        vertLine: { color: '#00d09e60' },
        horzLine: { color: '#00d09e60' },
      },
      rightPriceScale: { borderColor: '#30363d' },
      timeScale: {
        borderColor:     '#30363d',
        timeVisible:     true,
        secondsVisible:  false,
      },
      width:  containerRef.current.clientWidth,
      height: containerRef.current.clientHeight,
    })

    const series = chart.addCandlestickSeries({
      upColor:      '#00d09e',
      downColor:    '#ef5350',
      borderVisible: false,
      wickUpColor:   '#00d09e',
      wickDownColor: '#ef5350',
    })

    chartRef.current  = chart
    seriesRef.current = series

    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      chart.resize(width, height)
    })
    observer.observe(containerRef.current)

    return () => {
      observer.disconnect()
      chart.remove()
    }
  }, [])

  // ── 全量刷新（切换交易对 or 首次加载）─────────────────────────────────
  useEffect(() => {
    if (!seriesRef.current || klines.length === 0) return
    // 去重并仅展示最近 120 根，避免时间重复导致竖线/图形异常
    const visible = dedupeByOpenTimeAsc(klines).slice(-120)
    if (visible.length === 0) return
    seriesRef.current.setData(visible.map(toChartBar))
    chartRef.current?.timeScale().fitContent()
  }, [symbol, klines])                 // symbol 或数据变化时全量更新

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      {/* 交易对水印 */}
      <span style={{
        position:   'absolute',
        top: 12, left: 16,
        fontSize:   13,
        color:      '#8b949e',
        fontWeight: 600,
        zIndex:     1,
        pointerEvents: 'none',
      }}>
        {symbol} · 1min
      </span>
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
    </div>
  )
}
