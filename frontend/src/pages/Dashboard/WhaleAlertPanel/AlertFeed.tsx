import { useRef, useEffect, useState } from 'react'
import { useMarketStore, WhaleAlert } from '../../../store/marketStore'
import { BORDER, TEXT_SUB, UP_COLOR, DOWN_COLOR, ACCENT, BG_CARD } from '../../../theme/darkTheme'
import dayjs from 'dayjs'

// ── Severity config ────────────────────────────────────────────────────────

const SEV: Record<number, { label: string; color: string }> = {
  1: { label: '警告', color: '#F0B90B' },
  2: { label: '严重', color: '#F6465D' },
  3: { label: '极端', color: '#FF2D55' },
}

// ── Pressure Gauge ─────────────────────────────────────────────────────────
// SVG coordinate system: CX/CY is the arc center, R is track radius

const CX = 120, CY = 86, R = 76, NL = 63

function polarToCart(angleDeg: number, radius = R) {
  const rad = (angleDeg * Math.PI) / 180
  return { x: CX + radius * Math.cos(rad), y: CY - radius * Math.sin(rad) }
}

function arcPath(startDeg: number, endDeg: number, r = R): string {
  const s = polarToCart(startDeg, r)
  const e = polarToCart(endDeg, r)
  // sweep=0: counter-clockwise in SVG = draws the upper semicircle
  return `M ${s.x.toFixed(2)} ${s.y.toFixed(2)} A ${r} ${r} 0 0 0 ${e.x.toFixed(2)} ${e.y.toFixed(2)}`
}

function PressureGauge({
  buyRatio,
  buyCount,
  sellCount,
}: {
  buyRatio:  number
  buyCount:  number
  sellCount: number
}) {
  const [animRatio, setAnimRatio] = useState(buyRatio)
  const animRatioRef = useRef(buyRatio)
  const rafRef       = useRef(0)

  // Smooth needle animation via rAF
  useEffect(() => {
    cancelAnimationFrame(rafRef.current)
    const from = animRatioRef.current
    const to   = buyRatio
    const t0   = Date.now()

    function step() {
      const t     = Math.min((Date.now() - t0) / 750, 1)
      const eased = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t
      const val   = from + (to - from) * eased
      animRatioRef.current = val
      setAnimRatio(val)
      if (t < 1) rafRef.current = requestAnimationFrame(step)
    }

    rafRef.current = requestAnimationFrame(step)
    return () => cancelAnimationFrame(rafRef.current)
  }, [buyRatio])

  // Needle geometry
  const needleAngleDeg = (1 - animRatio) * 180
  const tip = polarToCart(needleAngleDeg, NL)
  const bL  = polarToCart(needleAngleDeg + 90, 5.5)
  const bR  = polarToCart(needleAngleDeg - 90, 5.5)

  // Label based on final (not animated) ratio to avoid flicker
  let label: string, lColor: string
  if      (buyRatio >= 0.68) { label = '买压强势'; lColor = UP_COLOR }
  else if (buyRatio >= 0.55) { label = '多头偏强'; lColor = '#3DD68C' }
  else if (buyRatio <= 0.32) { label = '卖压强势'; lColor = DOWN_COLOR }
  else if (buyRatio <= 0.45) { label = '空头偏强'; lColor = '#FF8250' }
  else                       { label = '多空均衡'; lColor = ACCENT }

  const total   = buyCount + sellCount
  const buyPct  = Math.round(buyRatio * 100)
  const sellPct = 100 - buyPct

  return (
    <div style={{ padding: '10px 12px 8px' }}>
      {/* Section title */}
      <div
        style={{
          display:        'flex',
          justifyContent: 'space-between',
          marginBottom:   4,
        }}
      >
        <span style={{ fontSize: 11, color: TEXT_SUB, fontWeight: 500 }}>鲸鱼买卖压力</span>
        <span style={{ fontSize: 11, color: `${TEXT_SUB}77` }}>近 {total} 笔告警</span>
      </div>

      {/* SVG Gauge */}
      <svg
        viewBox="0 0 240 110"
        width="100%"
        style={{ display: 'block' }}
      >
        {/* Background track */}
        <path
          d={arcPath(180, 0)}
          fill="none"
          stroke="#2B3139"
          strokeWidth="14"
          strokeLinecap="round"
        />

        {/* Sell zone (left third) */}
        <path
          d={arcPath(180, 120)}
          fill="none"
          stroke={DOWN_COLOR}
          strokeWidth="13"
          strokeLinecap="round"
          opacity="0.45"
        />
        {/* Neutral zone (middle) */}
        <path
          d={arcPath(120, 60)}
          fill="none"
          stroke={ACCENT}
          strokeWidth="13"
          strokeLinecap="round"
          opacity="0.28"
        />
        {/* Buy zone (right third) */}
        <path
          d={arcPath(60, 0)}
          fill="none"
          stroke={UP_COLOR}
          strokeWidth="13"
          strokeLinecap="round"
          opacity="0.45"
        />

        {/* Zone edge labels */}
        <text x="26"  y="84" fill={DOWN_COLOR} fontSize="9"   textAnchor="middle" fontWeight="700" opacity="0.8">卖</text>
        <text x="214" y="84" fill={UP_COLOR}   fontSize="9"   textAnchor="middle" fontWeight="700" opacity="0.8">买</text>
        <text x="120" y="13" fill={ACCENT}     fontSize="7.5" textAnchor="middle" opacity="0.5">中性</text>

        {/* Needle triangle */}
        <polygon
          points={`${tip.x.toFixed(1)},${tip.y.toFixed(1)} ${bL.x.toFixed(1)},${bL.y.toFixed(1)} ${bR.x.toFixed(1)},${bR.y.toFixed(1)}`}
          fill={lColor}
          opacity="0.92"
        />

        {/* Center cap */}
        <circle cx={CX} cy={CY} r="8"   fill={BG_CARD} stroke="#2B3139" strokeWidth="1.5" />
        <circle cx={CX} cy={CY} r="3.5" fill={lColor} />

        {/* Pressure label below center */}
        <text
          x={CX}
          y={CY + 19}
          fill={lColor}
          fontSize="10"
          fontWeight="bold"
          textAnchor="middle"
        >
          {label}
        </text>
      </svg>

      {/* Proportional bar + percentage stats */}
      <div style={{ display: 'flex', gap: 5, marginTop: -2 }}>
        {/* Sell side */}
        <div
          style={{
            flex:          sellPct,
            display:       'flex',
            flexDirection: 'column',
            alignItems:    'flex-start',
            gap:           3,
            minWidth:      0,
          }}
        >
          <div
            style={{
              width:        '100%',
              height:       3,
              borderRadius: 2,
              background:   DOWN_COLOR,
              opacity:      0.65,
              transition:   'flex 0.5s ease',
            }}
          />
          <span style={{ fontSize: 10, color: DOWN_COLOR, whiteSpace: 'nowrap' }}>
            {sellPct}% · {sellCount} 笔
          </span>
        </div>

        {/* Buy side */}
        <div
          style={{
            flex:          buyPct,
            display:       'flex',
            flexDirection: 'column',
            alignItems:    'flex-end',
            gap:           3,
            minWidth:      0,
          }}
        >
          <div
            style={{
              width:        '100%',
              height:       3,
              borderRadius: 2,
              background:   UP_COLOR,
              opacity:      0.65,
              transition:   'flex 0.5s ease',
            }}
          />
          <span style={{ fontSize: 10, color: UP_COLOR, whiteSpace: 'nowrap' }}>
            {buyCount} 笔 · {buyPct}%
          </span>
        </div>
      </div>
    </div>
  )
}

// ── Alert Row ──────────────────────────────────────────────────────────────

function AlertRow({ alert, flash }: { alert: WhaleAlert; flash: boolean }) {
  const ref    = useRef<HTMLDivElement>(null)
  const isBuy  = alert.direction === 'BUY'
  const sev    = SEV[alert.severity] ?? SEV[1]
  const dirColor = isBuy ? UP_COLOR : DOWN_COLOR

  useEffect(() => {
    if (!flash || !ref.current) return
    ref.current.animate(
      [
        { backgroundColor: isBuy ? '#0ECB8115' : '#F6465D15' },
        { backgroundColor: 'transparent' },
      ],
      { duration: 1400, easing: 'ease-out', fill: 'forwards' }
    )
  }, [flash])

  return (
    <div
      ref={ref}
      style={{
        padding:       '9px 14px',
        borderBottom:  `1px solid ${BORDER}`,
        display:       'flex',
        flexDirection: 'column',
        gap:           5,
        cursor:        'default',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
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
        <span
          style={{
            width:        5,
            height:       5,
            borderRadius: '50%',
            background:   sev.color,
            display:      'inline-block',
            flexShrink:   0,
          }}
        />
        <span style={{ fontSize: 11, color: sev.color, fontWeight: 600 }}>{sev.label}</span>
        <span style={{ marginLeft: 'auto', fontSize: 11, color: TEXT_SUB, fontVariantNumeric: 'tabular-nums' }}>
          {dayjs(alert.alertTime).format('HH:mm:ss')}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: dirColor, fontVariantNumeric: 'tabular-nums' }}>
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

  // 按金额加权计算买卖压力（一笔 $500k 买单不能和一笔 $50k 等权）
  const recent     = alerts.slice(0, 100)
  const buyCount   = recent.filter((a) => a.direction === 'BUY').length
  const sellCount  = recent.filter((a) => a.direction === 'SELL').length
  const buyVolume  = recent.filter((a) => a.direction === 'BUY' ).reduce((s, a) => s + Number(a.totalQuote), 0)
  const sellVolume = recent.filter((a) => a.direction === 'SELL').reduce((s, a) => s + Number(a.totalQuote), 0)
  const totalVolume = buyVolume + sellVolume
  const buyRatio   = totalVolume > 0 ? buyVolume / totalVolume : 0.5

  const hasAlerts = alerts.length > 0

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>

      {/* Header */}
      <div
        style={{
          padding:        '8px 14px',
          borderBottom:   `1px solid ${BORDER}`,
          display:        'flex',
          alignItems:     'center',
          justifyContent: 'space-between',
          flexShrink:     0,
        }}
      >
        <span style={{ fontSize: 11, color: TEXT_SUB, fontWeight: 500 }}>
          巨鲸动向 · 实时推送
        </span>
        {hasAlerts && (
          <span
            style={{
              fontSize:     11,
              color:        ACCENT,
              background:   `${ACCENT}18`,
              padding:      '1px 8px',
              borderRadius: 10,
              fontWeight:   600,
            }}
          >
            {Math.min(alerts.length, 200)}
          </span>
        )}
      </div>

      {/* Pressure gauge — only when there is data */}
      {hasAlerts && (
        <>
          <div style={{ flexShrink: 0 }}>
            <PressureGauge
              buyRatio={buyRatio}
              buyCount={buyCount}
              sellCount={sellCount}
            />
          </div>
          <div style={{ borderBottom: `1px solid ${BORDER}`, flexShrink: 0, margin: '0 0 0 0' }} />
        </>
      )}

      {/* Scrollable alert list */}
      <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
        {!hasAlerts ? (
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
