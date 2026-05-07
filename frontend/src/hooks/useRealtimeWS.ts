import { useEffect, useRef } from 'react'
import { KlineBar, WhaleAlert, useMarketStore } from '../store/marketStore'

// 指数退避参数
const RECONNECT_BASE_MS = 3_000
const RECONNECT_MAX_MS  = 30_000

export function useRealtimeWS() {
  // 需要实时推送时：VITE_ENABLE_WS=1 npm run dev:local-ws
  const enabled = import.meta.env.VITE_ENABLE_WS === '1'

  const prependAlerts     = useMarketStore((s) => s.prependAlerts)
  const updateLatestKline = useMarketStore((s) => s.updateLatestKline)
  const setWsConnected    = useMarketStore((s) => s.setWsConnected)

  const symbolRef = useRef(useMarketStore.getState().symbol)
  useEffect(() =>
    useMarketStore.subscribe((s) => { symbolRef.current = s.symbol })
  , [])

  useEffect(() => {
    if (!enabled) {
      setWsConnected(false)
      return
    }

    let ws:        WebSocket | null = null
    let destroyed: boolean          = false
    let timer:     ReturnType<typeof setTimeout>
    let attempt    = 0

    function connect() {
      if (destroyed) return
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      ws = new WebSocket(`${proto}//${location.host}/ws/realtime`)

      ws.onopen = () => {
        attempt = 0           // 成功后重置退避计数
        setWsConnected(true)
      }

      ws.onmessage = ({ data }) => {
        try {
          const msg = JSON.parse(data as string)
          const sym = symbolRef.current

          if (msg.type === 'whale.alert.batch') {
            const items: WhaleAlert[] = (msg.items ?? []).filter(
              (a: WhaleAlert) => a.symbol === sym
            )
            if (items.length) prependAlerts(items)
          }

          if (msg.type === 'kline.latest.batch') {
            const bar: KlineBar | undefined = (msg.items ?? []).find(
              (b: KlineBar) => b.symbol === sym
            )
            if (bar) updateLatestKline(bar)
          }
        } catch {
          // ignore parse errors
        }
      }

      ws.onclose = () => {
        setWsConnected(false)
        if (!destroyed) {
          // 指数退避：3s → 6s → 12s → … → 最大 30s
          const delay = Math.min(RECONNECT_BASE_MS * 2 ** attempt, RECONNECT_MAX_MS)
          attempt++
          timer = setTimeout(connect, delay)
        }
      }

      ws.onerror = () => ws?.close()
    }

    connect()
    return () => {
      destroyed = true
      clearTimeout(timer)
      setWsConnected(false)
      ws?.close()
    }
  }, [enabled, prependAlerts, updateLatestKline, setWsConnected])
}
