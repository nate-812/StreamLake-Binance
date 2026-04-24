import { useRef, useEffect } from 'react'
import { Tag, Empty } from 'antd'
import { useMarketStore, WhaleAlert } from '../../../store/marketStore'
import { BG_CARD, BORDER, TEXT_SUB, UP_COLOR, DOWN_COLOR } from '../../../theme/darkTheme'
import dayjs from 'dayjs'

const SEV_COLOR: Record<number, string> = { 1: '#faad14', 2: '#ff7875', 3: DOWN_COLOR }
const SEV_LABEL: Record<number, string> = { 1: '警告', 2: '严重', 3: '极端' }

function AlertRow({ alert, isNew }: { alert: WhaleAlert; isNew: boolean }) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isNew || !ref.current) return
    ref.current.animate(
      [{ background: '#00d09e22' }, { background: 'transparent' }],
      { duration: 1200, easing: 'ease-out' }
    )
  }, [isNew])

  const isBuy = alert.direction === 'BUY'

  return (
    <div ref={ref} style={{
      padding: '8px 12px',
      borderBottom: `1px solid ${BORDER}`,
      display: 'flex',
      flexDirection: 'column',
      gap: 4,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Tag color={isBuy ? 'cyan' : 'red'} style={{ margin: 0, fontWeight: 700 }}>
          {alert.direction}
        </Tag>
        <Tag color={SEV_COLOR[alert.severity]} style={{ margin: 0, fontSize: 11 }}>
          {SEV_LABEL[alert.severity]}
        </Tag>
        <span style={{ color: TEXT_SUB, fontSize: 11 }}>
          {dayjs(alert.alertTime).format('HH:mm:ss')}
        </span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <span style={{ color: isBuy ? UP_COLOR : DOWN_COLOR, fontSize: 13, fontWeight: 600 }}>
          {Number(alert.totalQuote).toLocaleString(undefined, { maximumFractionDigits: 0 })} USDT
        </span>
        <span style={{ color: TEXT_SUB, fontSize: 11 }}>触发 {alert.triggerCount} 笔</span>
      </div>
    </div>
  )
}

export default function AlertFeed() {
  const alerts     = useMarketStore((s) => s.alerts)
  const prevCount  = useRef(0)
  const newCount   = alerts.length - prevCount.current
  prevCount.current = alerts.length

  return (
    <div style={{
      background: BG_CARD,
      border:     `1px solid ${BORDER}`,
      borderRadius: 8,
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      <div style={{
        padding: '10px 12px',
        borderBottom: `1px solid ${BORDER}`,
        fontSize: 12,
        color: TEXT_SUB,
        display: 'flex',
        justifyContent: 'space-between',
      }}>
        <span>实时告警（最近）</span>
        {alerts.length > 0 && (
          <span style={{ color: '#00d09e' }}>{Math.min(alerts.length, 200)} 条</span>
        )}
      </div>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {alerts.length === 0 ? (
          <div style={{ padding: 32 }}>
            <Empty description={<span style={{ color: TEXT_SUB }}>暂无告警</span>} />
          </div>
        ) : (
          alerts.slice(0, 30).map((a, i) => (
            <AlertRow key={a.alertId} alert={a} isNew={i < newCount} />
          ))
        )}
      </div>
    </div>
  )
}
