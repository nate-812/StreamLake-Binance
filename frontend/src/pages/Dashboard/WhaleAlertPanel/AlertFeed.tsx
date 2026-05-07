import { useRef, useEffect, useState } from 'react'
import { useMarketStore, WhaleAlert } from '../../../store/marketStore'
import { BORDER, TEXT_SUB, UP_COLOR, DOWN_COLOR, ACCENT } from '../../../theme/darkTheme'
import dayjs from 'dayjs'

// ── Severity config ────────────────────────────────────────────────────────

const SEV: Record<number, { label: string; color: string; dot: string }> = {
  1: { label: '警告', color: '#F0B90B',  dot: '#F0B90B' },
  2: { label: '严重', color: '#F6465D',  dot: '#F6465D' },
  3: { label: '极端', color: '#FF2D55',  dot: '#FF2D55' },
}

// ── Alert Row ──────────────────────────────────────────────────────────────

function AlertRow({ alert, flash }: { alert: WhaleAlert; flash: boolean }) {
  const ref     = useRef<HTMLDivElement>(null)
  const isBuy   = alert.direction === 'BUY'
  const sev     = SEV[alert.severity] ?? SEV[1]
  const dirColor = isBuy ? UP_COLOR : DOWN_COLOR

  useEffect(() => {
    if (!flash || !ref.current) return
    ref.current.animate(
      [
        { backgroundColor: isBuy ? '#0ECB8118' : '#F6465D18' },
        { backgroundColor: 'transparent' },
      ],
      { duration: 1400, easing: 'ease-out', fill: 'forwards' }
    )
  }, [flash])

  return (
    <div
      ref={ref}
      style={{
        padding:      '10px 14px',
        borderBottom: `1px solid ${BORDER}`,
        display:      'flex',
        flexDirection: 'column',
        gap:          6,
        cursor:       'default',
      }}
    >
      {/* Row 1: Direction badge, severity dot, time */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {/* Direction pill */}
        <span
          style={{
            fontSize:     11,
            fontWeight:   700,
            color:        isBuy ? '#0B0E11' : '#EAECEF',
            background:   dirColor,
            padding:      '2px 8px',
            borderRadius: 3,
            lineHeight:   1.4,
            letterSpacing: 0.5,
          }}
        >
          {alert.direction}
        </span>

        {/* Severity dot */}
        <span
          style={{
            width:        6,
            height:       6,
            borderRadius: '50%',
            background:   sev.dot,
            display:      'inline-block',
            flexShrink:   0,
          }}
        />
        <span style={{ fontSize: 11, color: sev.color, fontWeight: 600 }}>{sev.label}</span>

        {/* Time — right aligned */}
        <span style={{ marginLeft: 'auto', fontSize: 11, color: TEXT_SUB, fontVariantNumeric: 'tabular-nums' }}>
          {dayjs(alert.alertTime).format('HH:mm:ss')}
        </span>
      </div>

      {/* Row 2: Amount and trigger count */}
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <span
          style={{
            fontSize:          14,
            fontWeight:        600,
            color:             dirColor,
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {Number(alert.totalQuote).toLocaleString(undefined, { maximumFractionDigits: 0 })}
          <span style={{ fontSize: 11, fontWeight: 400, color: TEXT_SUB, marginLeft: 4 }}>USDT</span>
        </span>
        <span style={{ fontSize: 11, color: TEXT_SUB }}>
          聚合 <span style={{ color: '#EAECEF', fontWeight: 500 }}>{alert.triggerCount}</span> 笔
        </span>
      </div>
    </div>
  )
}

// ── Alert Feed ─────────────────────────────────────────────────────────────

export default function AlertFeed() {
  const alerts    = useMarketStore((s) => s.alerts)
  const prevCount = useRef(0)
  const [flashN, setFlashN] = useState(0)

  useEffect(() => {
    const n = alerts.length - prevCount.current
    prevCount.current = alerts.length
    if (n > 0) setFlashN(n)
  }, [alerts])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Feed Header */}
      <div
        style={{
          padding:      '8px 14px',
          borderBottom: `1px solid ${BORDER}`,
          display:      'flex',
          alignItems:   'center',
          justifyContent: 'space-between',
          flexShrink:   0,
        }}
      >
        <span style={{ fontSize: 11, color: TEXT_SUB, fontWeight: 500 }}>
          巨鲸动向 · 实时推送
        </span>
        {alerts.length > 0 && (
          <span
            style={{
              fontSize:     11,
              color:        ACCENT,
              background:   '#F0B90B18',
              padding:      '1px 8px',
              borderRadius: 10,
              fontWeight:   600,
            }}
          >
            {Math.min(alerts.length, 200)}
          </span>
        )}
      </div>

      {/* Scrollable list */}
      <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
        {alerts.length === 0 ? (
          <div
            style={{
              height:         '100%',
              display:        'flex',
              flexDirection:  'column',
              alignItems:     'center',
              justifyContent: 'center',
              gap:            10,
              color:          TEXT_SUB,
            }}
          >
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none">
              <path
                d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"
                stroke={TEXT_SUB}
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d="M13.73 21a2 2 0 0 1-3.46 0"
                stroke={TEXT_SUB}
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            <span style={{ fontSize: 12 }}>暂无告警信号</span>
          </div>
        ) : (
          alerts
            .slice(0, 50)
            .map((a, i) => (
              <AlertRow key={a.alertId} alert={a} flash={i < flashN} />
            ))
        )}
      </div>
    </div>
  )
}
