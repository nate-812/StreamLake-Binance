import { useEffect, useState, useMemo, ReactNode } from 'react'
import { Layout, Select, Button } from 'antd'
import { RobotOutlined, ThunderboltOutlined, ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import { useMarketStore, SYMBOL_LIST } from '../../store/marketStore'
import { fetchKlines, fetchAlerts, fetchSummary } from '../../api'
import { useRealtimeWS } from '../../hooks/useRealtimeWS'
import TradingViewChart from './KlinePanel/TradingViewChart'
import AlertFeed from './WhaleAlertPanel/AlertFeed'
import DiagnosisDrawer from './AiDiagnosis/DiagnosisDrawer'
import Heatmap from './MarketHeatmap/Heatmap'
import { BG_PAGE, BG_CARD, BORDER, TEXT_SUB, TEXT_MAIN, UP_COLOR, DOWN_COLOR, ACCENT } from '../../theme/darkTheme'

const { Header, Content } = Layout

// ── Helpers ────────────────────────────────────────────────────────────────

function fmtPrice(n: number | null | undefined): string {
  if (n == null) return '--'
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtCompact(n: number): string {
  if (n >= 1e9) return (n / 1e9).toFixed(2) + 'B'
  if (n >= 1e6) return (n / 1e6).toFixed(2) + 'M'
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K'
  return n.toFixed(0)
}

// ── Header Stat Item ───────────────────────────────────────────────────────

function StatItem({
  label,
  value,
  color,
}: {
  label: string
  value: string
  color?: string
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 3, padding: '0 14px' }}>
      <span style={{ fontSize: 11, color: TEXT_SUB, lineHeight: 1, whiteSpace: 'nowrap' }}>
        {label}
      </span>
      <span
        style={{
          fontSize: 13,
          color: color ?? TEXT_MAIN,
          fontWeight: 500,
          lineHeight: 1,
          fontVariantNumeric: 'tabular-nums',
          whiteSpace: 'nowrap',
        }}
      >
        {value}
      </span>
    </div>
  )
}

// ── Right Panel Tabs (passes height correctly to children) ─────────────────

function PanelTabs({
  tabs,
}: {
  tabs: { key: string; label: string; content: ReactNode }[]
}) {
  const [active, setActive] = useState(tabs[0].key)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Tab Bar */}
      <div
        style={{
          display: 'flex',
          borderBottom: `1px solid ${BORDER}`,
          flexShrink: 0,
          padding: '0 4px',
        }}
      >
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setActive(t.key)}
            style={{
              padding: '9px 14px',
              fontSize: 12,
              fontWeight: active === t.key ? 600 : 400,
              color: active === t.key ? TEXT_MAIN : TEXT_SUB,
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              borderBottom: `2px solid ${active === t.key ? ACCENT : 'transparent'}`,
              marginBottom: -1,
              transition: 'color 0.15s, border-color 0.15s',
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab Content — render all, show/hide via display to preserve scroll */}
      <div style={{ flex: 1, overflow: 'hidden', minHeight: 0, position: 'relative' }}>
        {tabs.map((t) => (
          <div
            key={t.key}
            style={{
              position: 'absolute',
              inset: 0,
              display: t.key === active ? 'flex' : 'none',
              flexDirection: 'column',
            }}
          >
            {t.content}
          </div>
        ))}
      </div>
    </div>
  )
}

// ── Dashboard ──────────────────────────────────────────────────────────────

export default function Dashboard() {
  const { symbol, setSymbol, setKlines, prependAlerts, setSummary } = useMarketStore()
  const summary = useMarketStore((s) => s.summary)
  const klines  = useMarketStore((s) => s.klines)
  const [drawerOpen, setDrawerOpen] = useState(false)

  useRealtimeWS()

  useEffect(() => {
    fetchKlines(symbol, 3000).then(setKlines).catch(console.error)
    fetchAlerts(symbol).then(prependAlerts).catch(console.error)
    fetchSummary(symbol).then(setSummary).catch(console.error)
  }, [symbol])

  useEffect(() => {
    const id = window.setInterval(() => {
      fetchSummary(symbol).then(setSummary).catch(() => null)
    }, 30_000)
    return () => window.clearInterval(id)
  }, [symbol])

  // 近 24h 最高/最低（取最近 1440 根 1m K 线近似）
  const { h24, l24 } = useMemo(() => {
    const slice = klines.slice(-1440)
    if (slice.length === 0) return { h24: null, l24: null }
    return {
      h24: Math.max(...slice.map((k) => Number(k.high))),
      l24: Math.min(...slice.map((k) => Number(k.low))),
    }
  }, [klines])

  const pct        = summary?.priceChangePct24h ?? 0
  const isUp       = pct >= 0
  const priceColor = isUp ? UP_COLOR : DOWN_COLOR
  const baseAsset  = symbol.replace('USDT', '')

  return (
    <Layout style={{ height: '100vh', background: BG_PAGE, overflow: 'hidden' }}>

      {/* ── Header ── */}
      <Header
        style={{
          background:   BG_CARD,
          borderBottom: `1px solid ${BORDER}`,
          display:      'flex',
          alignItems:   'center',
          padding:      '0 16px',
          height:       60,
          lineHeight:   'normal',
          flexShrink:   0,
          gap:          0,
        }}
      >
        {/* Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 148, flexShrink: 0 }}>
          <ThunderboltOutlined style={{ color: ACCENT, fontSize: 17 }} />
          <span style={{ fontSize: 15, fontWeight: 700, color: TEXT_MAIN, letterSpacing: 0.3 }}>
            StreamLake
          </span>
          <span style={{ fontSize: 11, color: TEXT_SUB }}>量化大屏</span>
        </div>

        {/* Divider */}
        <div style={{ width: 1, height: 28, background: BORDER, flexShrink: 0, margin: '0 16px' }} />

        {/* Symbol Selector */}
        <Select
          value={symbol}
          onChange={setSymbol}
          style={{ width: 150, flexShrink: 0 }}
          options={SYMBOL_LIST.map((s) => ({
            label: `${s.replace('USDT', '')} / USDT`,
            value: s,
          }))}
        />

        {/* Divider */}
        <div style={{ width: 1, height: 28, background: BORDER, flexShrink: 0, margin: '0 20px' }} />

        {/* Live Price */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0 }}>
          <span
            style={{
              fontSize: 22,
              fontWeight: 600,
              color: priceColor,
              fontVariantNumeric: 'tabular-nums',
              letterSpacing: -0.5,
              lineHeight: 1,
            }}
          >
            {fmtPrice(summary?.lastPrice)}
          </span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            <span style={{ fontSize: 12, fontWeight: 600, color: priceColor, lineHeight: 1 }}>
              {isUp ? <ArrowUpOutlined /> : <ArrowDownOutlined />}{' '}
              {Math.abs(pct).toFixed(2)}%
            </span>
            <span
              style={{
                fontSize: 11,
                color: priceColor,
                lineHeight: 1,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {isUp ? '+' : ''}{Number(summary?.priceChange24h ?? 0).toFixed(2)}
            </span>
          </div>
        </div>

        {/* Divider */}
        <div style={{ width: 1, height: 28, background: BORDER, flexShrink: 0, margin: '0 4px 0 16px' }} />

        {/* 24H Stats */}
        <div style={{ display: 'flex', alignItems: 'center', flex: 1, overflow: 'hidden' }}>
          <StatItem label="24H 最高" value={fmtPrice(h24)} color={UP_COLOR} />
          <StatItem label="24H 最低" value={fmtPrice(l24)} color={DOWN_COLOR} />
          <StatItem
            label={`24H 量 (${baseAsset})`}
            value={summary ? fmtCompact(Number(summary.volume24h)) : '--'}
          />
          <StatItem
            label="24H 额 (USDT)"
            value={summary ? fmtCompact(Number(summary.quoteVolume24h)) : '--'}
          />
          {(summary?.whaleAlertCount1h ?? 0) > 0 && (
            <StatItem
              label="1H 巨鲸"
              value={`${summary!.whaleAlertCount1h} 笔`}
              color={ACCENT}
            />
          )}
          {(summary?.riskTriggerCount24h ?? 0) > 0 && (
            <StatItem
              label="24H 风控"
              value={`${summary!.riskTriggerCount24h} 次`}
              color={DOWN_COLOR}
            />
          )}
        </div>

        {/* AI Diagnosis Button */}
        <Button
          type="primary"
          icon={<RobotOutlined />}
          onClick={() => setDrawerOpen(true)}
          style={{
            background:  ACCENT,
            border:      'none',
            color:       '#0B0E11',
            fontWeight:  600,
            flexShrink:  0,
          }}
        >
          AI 诊断
        </Button>
      </Header>

      {/* ── Main Content ── */}
      <Content
        style={{
          height:   'calc(100vh - 60px)',
          padding:  '10px',
          display:  'flex',
          gap:      10,
          overflow: 'hidden',
        }}
      >
        {/* K-line Chart — 75% */}
        <div
          style={{
            flex:          '0 0 calc(75% - 5px)',
            background:    BG_CARD,
            border:        `1px solid ${BORDER}`,
            borderRadius:  6,
            overflow:      'hidden',
            display:       'flex',
            flexDirection: 'column',
          }}
        >
          <TradingViewChart />
        </div>

        {/* Right Panel — 25% */}
        <div
          style={{
            flex:         '0 0 calc(25% - 5px)',
            background:   BG_CARD,
            border:       `1px solid ${BORDER}`,
            borderRadius: 6,
            overflow:     'hidden',
          }}
        >
          <PanelTabs
            tabs={[
              {
                key:     'alerts',
                label:   '实时告警',
                content: <AlertFeed />,
              },
              {
                key:     'heatmap',
                label:   '热力图',
                content: (
                  <div style={{ padding: 12, overflow: 'auto', height: '100%', boxSizing: 'border-box' }}>
                    <Heatmap />
                  </div>
                ),
              },
            ]}
          />
        </div>
      </Content>

      <DiagnosisDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </Layout>
  )
}
