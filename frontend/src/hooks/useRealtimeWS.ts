import { useEffect, useRef } from 'react'
import { KlineBar, WhaleAlert, useMarketStore } from '../store/marketStore'

const RECONNECT_MS = 3000

export function useRealtimeWS() {
  const prependAlerts     = useMarketStore((s) => s.prependAlerts)
  const updateLatestKline = useMarketStore((s) => s.updateLatestKline)

  // 用 ref 读取最新 symbol，避免 effect 闭包过期
  const symbolRef = useRef(useMarketStore.getState().symbol)
  useEffect(() =>
    useMarketStore.subscribe((s) => { symbolRef.current = s.symbol })
  , [])

  useEffect(() => {
    let ws: WebSocket | null = null
    let destroyed = false
    let timer: ReturnType<typeof setTimeout>

    function connect() {
      if (destroyed) return
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      ws = new WebSocket(`${proto}//${location.host}/ws/realtime`)

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
        if (!destroyed) timer = setTimeout(connect, RECONNECT_MS)
      }
      ws.onerror = () => ws?.close()
    }

    connect()
    return () => {
      destroyed = true
      clearTimeout(timer)
      ws?.close()
    }
  }, [prependAlerts, updateLatestKline])
}
