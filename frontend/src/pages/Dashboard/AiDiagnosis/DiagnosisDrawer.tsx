import { useState, useEffect } from 'react'
import { Drawer, Button } from 'antd'
import {
  RobotOutlined,
  ReloadOutlined,
  CloseOutlined,
  CopyOutlined,
  CheckOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import ReactMarkdown from 'react-markdown'
import dayjs from 'dayjs'
import { useMarketStore } from '../../../store/marketStore'
import { fetchDiagnosis } from '../../../api'
import {
  BG_CARD, BG_PAGE, BORDER,
  TEXT_SUB, TEXT_MAIN,
  ACCENT, UP_COLOR, DOWN_COLOR,
} from '../../../theme/darkTheme'

// ── Analysis step config ───────────────────────────────────────────────────

const STEPS = [
  '正在获取近期 K 线数据',
  '分析量价关系与趋势',
  '识别巨鲸动向信号',
  '综合生成诊断报告',
]

// ── Sub-components ─────────────────────────────────────────────────────────

function LoadingView({ symbol }: { symbol: string }) {
  const [stepIdx,   setStepIdx]   = useState(0)
  const [dotCount,  setDotCount]  = useState(1)

  useEffect(() => {
    const s = window.setInterval(() => setStepIdx((i) => (i + 1) % STEPS.length), 1800)
    const d = window.setInterval(() => setDotCount((n) => (n % 3) + 1), 480)
    return () => { window.clearInterval(s); window.clearInterval(d) }
  }, [])

  return (
    <div
      style={{
        flex:           1,
        display:        'flex',
        flexDirection:  'column',
        alignItems:     'center',
        justifyContent: 'center',
        gap:            32,
        padding:        48,
      }}
    >
      {/* AI Icon orb */}
      <div
        style={{
          width:          84,
          height:         84,
          borderRadius:   22,
          background:     `linear-gradient(135deg, ${ACCENT}1A 0%, ${ACCENT}06 100%)`,
          border:         `1px solid ${ACCENT}33`,
          display:        'flex',
          alignItems:     'center',
          justifyContent: 'center',
          boxShadow:      `0 0 40px ${ACCENT}12, inset 0 1px 0 ${ACCENT}22`,
        }}
      >
        <RobotOutlined style={{ fontSize: 38, color: ACCENT }} />
      </div>

      {/* Status text */}
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 15, fontWeight: 600, color: TEXT_MAIN, marginBottom: 10, letterSpacing: 0.3 }}>
          正在分析&nbsp;
          <span style={{ color: ACCENT }}>{symbol}</span>
        </div>
        <div style={{ fontSize: 12, color: TEXT_SUB, height: 18 }}>
          {STEPS[stepIdx]}
          <span style={{ letterSpacing: 1 }}>{'.'.repeat(dotCount)}</span>
        </div>
      </div>

      {/* Progress indicators */}
      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        {STEPS.map((_, i) => (
          <div
            key={i}
            style={{
              height:     4,
              width:      i === stepIdx ? 28 : (i < stepIdx ? 10 : 8),
              borderRadius: 2,
              background: i <= stepIdx ? ACCENT : `${ACCENT}28`,
              transition: 'all 0.45s ease',
            }}
          />
        ))}
      </div>

      <div style={{ fontSize: 11, color: `${TEXT_SUB}88`, marginTop: -12 }}>
        Powered by DeepSeek · 约需 10–20 秒
      </div>
    </div>
  )
}

function ErrorView({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div style={{ padding: '32px 24px' }}>
      <div
        style={{
          padding:      20,
          borderRadius: 10,
          background:   `${DOWN_COLOR}0C`,
          border:       `1px solid ${DOWN_COLOR}2A`,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
          <div
            style={{
              width:          30,
              height:         30,
              borderRadius:   '50%',
              background:     `${DOWN_COLOR}1C`,
              display:        'flex',
              alignItems:     'center',
              justifyContent: 'center',
              flexShrink:     0,
            }}
          >
            <ExclamationCircleOutlined style={{ color: DOWN_COLOR, fontSize: 14 }} />
          </div>
          <span style={{ color: DOWN_COLOR, fontWeight: 600, fontSize: 13 }}>诊断请求失败</span>
        </div>
        <div style={{ fontSize: 12, color: TEXT_SUB, lineHeight: 1.7, wordBreak: 'break-all' }}>
          {message}
        </div>
        <button
          onClick={onRetry}
          style={{
            marginTop:    16,
            background:   'transparent',
            border:       `1px solid ${DOWN_COLOR}44`,
            color:        DOWN_COLOR,
            padding:      '5px 16px',
            borderRadius: 4,
            cursor:       'pointer',
            fontSize:     12,
            fontWeight:   500,
          }}
        >
          重新尝试
        </button>
      </div>
    </div>
  )
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  function handleCopy() {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2200)
    })
  }

  return (
    <Button
      size="small"
      icon={copied ? <CheckOutlined /> : <CopyOutlined />}
      onClick={handleCopy}
      style={{
        color:      copied ? UP_COLOR : TEXT_SUB,
        border:     `1px solid ${BORDER}`,
        background: 'transparent',
        fontSize:   12,
        transition: 'color 0.2s',
      }}
    >
      {copied ? '已复制' : '复制报告'}
    </Button>
  )
}

// ── Report markdown renderer ───────────────────────────────────────────────

function ReportView({ report, genTime }: { report: string; genTime: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
      {/* Scrollable content */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px 8px' }}>
        <ReactMarkdown
          components={{
            h1: ({ children }) => (
              <div style={{
                display:      'flex',
                alignItems:   'center',
                gap:          10,
                marginBottom: 20,
                paddingBottom: 14,
                borderBottom: `1px solid ${BORDER}`,
              }}>
                <div style={{
                  width:          6,
                  height:         6,
                  borderRadius:   '50%',
                  background:     ACCENT,
                  flexShrink:     0,
                  boxShadow:      `0 0 8px ${ACCENT}`,
                }} />
                <h1 style={{
                  margin:     0,
                  fontSize:   16,
                  fontWeight: 700,
                  color:      TEXT_MAIN,
                  lineHeight: 1.3,
                }}>
                  {children}
                </h1>
              </div>
            ),
            h2: ({ children }) => (
              <div style={{
                display:     'flex',
                alignItems:  'center',
                gap:         10,
                margin:      '22px 0 10px',
                paddingLeft: 12,
                borderLeft:  `2px solid ${ACCENT}`,
              }}>
                <span style={{ fontSize: 13, fontWeight: 700, color: TEXT_MAIN }}>{children}</span>
              </div>
            ),
            h3: ({ children }) => (
              <h3 style={{
                fontSize:   12,
                fontWeight: 600,
                color:      TEXT_SUB,
                margin:     '14px 0 6px',
                textTransform: 'uppercase',
                letterSpacing: 0.8,
              }}>
                {children}
              </h3>
            ),
            p: ({ children }) => (
              <p style={{ color: '#C0C6D0', margin: '7px 0', fontSize: 13, lineHeight: 1.85 }}>
                {children}
              </p>
            ),
            ul: ({ children }) => (
              <ul style={{ margin: '6px 0', paddingLeft: 18 }}>{children}</ul>
            ),
            li: ({ children }) => (
              <li style={{ color: '#C0C6D0', margin: '5px 0', fontSize: 13, lineHeight: 1.7 }}>
                {children}
              </li>
            ),
            strong: ({ children }) => (
              <strong style={{ color: TEXT_MAIN, fontWeight: 600 }}>{children}</strong>
            ),
            code: ({ children }) => (
              <code style={{
                background:   '#2B3139',
                padding:      '2px 7px',
                borderRadius: 4,
                fontSize:     12,
                color:        '#58B9FF',
                fontFamily:   'ui-monospace, monospace',
              }}>
                {children}
              </code>
            ),
            blockquote: ({ children }) => (
              <blockquote style={{
                margin:       '12px 0',
                padding:      '10px 14px',
                borderLeft:   `3px solid ${ACCENT}`,
                background:   `${ACCENT}08`,
                borderRadius: '0 6px 6px 0',
                color:        TEXT_SUB,
                fontSize:     13,
              }}>
                {children}
              </blockquote>
            ),
            hr: () => (
              <hr style={{ borderColor: BORDER, margin: '18px 0' }} />
            ),
          }}
        >
          {report}
        </ReactMarkdown>
      </div>

      {/* Footer */}
      <div
        style={{
          display:        'flex',
          alignItems:     'center',
          justifyContent: 'space-between',
          padding:        '12px 24px',
          borderTop:      `1px solid ${BORDER}`,
          flexShrink:     0,
        }}
      >
        <span style={{ fontSize: 11, color: `${TEXT_SUB}77` }}>
          生成于 {genTime} · Powered by DeepSeek
        </span>
        <CopyButton text={report} />
      </div>
    </div>
  )
}

// ── Main component ─────────────────────────────────────────────────────────

interface Props {
  open:    boolean
  onClose: () => void
}

export default function DiagnosisDrawer({ open, onClose }: Props) {
  const symbol = useMarketStore((s) => s.symbol)
  const [loading, setLoading] = useState(false)
  const [report,  setReport]  = useState<string | null>(null)
  const [error,   setError]   = useState<string | null>(null)
  const [diagSym, setDiagSym] = useState('')
  const [genTime, setGenTime] = useState<string | null>(null)

  async function runDiagnosis() {
    setLoading(true)
    setError(null)
    setReport(null)
    setDiagSym(symbol)
    try {
      const data = await fetchDiagnosis(symbol)
      setReport(data.reportMarkdown)
      setGenTime(dayjs().format('HH:mm'))
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  function handleOpen() {
    if (loading) return
    if (!report || diagSym !== symbol) {
      setReport(null)
      setError(null)
      runDiagnosis()
    }
  }

  return (
    <Drawer
      placement="right"
      width={580}
      open={open}
      onClose={onClose}
      closable={false}
      afterOpenChange={(vis) => vis && handleOpen()}
      styles={{
        header: { display: 'none' },
        body: {
          padding:       0,
          background:    BG_PAGE,
          display:       'flex',
          flexDirection: 'column',
          overflow:      'hidden',
        },
        wrapper: { boxShadow: `-6px 0 40px rgba(0,0,0,0.55)` },
      }}
    >
      {/* ── Custom Header ── */}
      <div
        style={{
          background:    BG_CARD,
          borderBottom:  `1px solid ${BORDER}`,
          padding:       '14px 20px',
          display:       'flex',
          alignItems:    'center',
          gap:           14,
          flexShrink:    0,
        }}
      >
        {/* AI Icon */}
        <div
          style={{
            width:          40,
            height:         40,
            borderRadius:   10,
            background:     `linear-gradient(135deg, ${ACCENT}22, ${ACCENT}08)`,
            border:         `1px solid ${ACCENT}33`,
            display:        'flex',
            alignItems:     'center',
            justifyContent: 'center',
            flexShrink:     0,
          }}
        >
          <RobotOutlined style={{ fontSize: 20, color: ACCENT }} />
        </div>

        {/* Title + meta */}
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 700, color: TEXT_MAIN, lineHeight: 1.2 }}>
            AI 市场诊断
          </div>
          <div style={{ fontSize: 11, color: TEXT_SUB, marginTop: 3, lineHeight: 1 }}>
            {diagSym
              ? `${diagSym}${genTime ? ' · 生成于 ' + genTime : ''}${loading ? ' · 分析中…' : ''}`
              : '选择交易对后自动分析'}
          </div>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={runDiagnosis}
            loading={loading}
            disabled={loading}
            style={{
              background: 'transparent',
              border:     `1px solid ${BORDER}`,
              color:      TEXT_SUB,
              fontSize:   12,
            }}
          >
            重新生成
          </Button>
          <button
            onClick={onClose}
            style={{
              width:          28,
              height:         28,
              borderRadius:   6,
              background:     'transparent',
              border:         `1px solid ${BORDER}`,
              display:        'flex',
              alignItems:     'center',
              justifyContent: 'center',
              cursor:         'pointer',
              color:          TEXT_SUB,
              padding:        0,
              flexShrink:     0,
            }}
          >
            <CloseOutlined style={{ fontSize: 11 }} />
          </button>
        </div>
      </div>

      {/* ── Body ── */}
      {loading && <LoadingView symbol={diagSym || symbol} />}
      {!loading && error && <ErrorView message={error} onRetry={runDiagnosis} />}
      {!loading && report && <ReportView report={report} genTime={genTime ?? '--'} />}

      {/* Empty state */}
      {!loading && !error && !report && (
        <div
          style={{
            flex:           1,
            display:        'flex',
            flexDirection:  'column',
            alignItems:     'center',
            justifyContent: 'center',
            gap:            16,
            color:          TEXT_SUB,
          }}
        >
          <RobotOutlined style={{ fontSize: 40, opacity: 0.25 }} />
          <span style={{ fontSize: 13 }}>点击"重新生成"开始分析</span>
        </div>
      )}
    </Drawer>
  )
}
