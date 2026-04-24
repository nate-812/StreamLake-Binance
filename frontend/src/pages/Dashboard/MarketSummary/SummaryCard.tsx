import { Row, Col, Statistic, Skeleton } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import { useMarketStore } from '../../../store/marketStore'
import { UP_COLOR, DOWN_COLOR, BG_CARD, BORDER, TEXT_SUB } from '../../../theme/darkTheme'

export default function SummaryCard() {
  const summary = useMarketStore((s) => s.summary)
  const symbol  = useMarketStore((s) => s.symbol)

  const pct  = summary?.priceChangePct24h ?? 0
  const isUp = pct >= 0
  const color = isUp ? UP_COLOR : DOWN_COLOR

  return (
    <div style={{
      background: BG_CARD,
      border:     `1px solid ${BORDER}`,
      borderRadius: 8,
      padding: 16,
    }}>
      <div style={{ fontSize: 12, color: TEXT_SUB, marginBottom: 8 }}>{symbol} · 行情概览</div>

      {!summary ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <>
          {/* 最新价 + 涨跌幅 */}
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, marginBottom: 12 }}>
            <span style={{ fontSize: 28, fontWeight: 700, color: '#e6edf3' }}>
              {Number(summary.lastPrice).toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </span>
            <span style={{ fontSize: 14, fontWeight: 600, color }}>
              {isUp ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              {' '}{Math.abs(pct).toFixed(2)}%
            </span>
          </div>

          <Row gutter={[8, 8]}>
            <Col span={12}>
              <Statistic
                title={<span style={{ color: TEXT_SUB, fontSize: 11 }}>24h 涨跌（USDT）</span>}
                value={Math.abs(Number(summary.priceChange24h))}
                precision={2}
                valueStyle={{ color, fontSize: 14 }}
                prefix={isUp ? '+' : '-'}
              />
            </Col>
            <Col span={12}>
              <Statistic
                title={<span style={{ color: TEXT_SUB, fontSize: 11 }}>24h 成交额</span>}
                value={Number(summary.quoteVolume24h)}
                precision={0}
                suffix="U"
                valueStyle={{ color: '#e6edf3', fontSize: 14 }}
                formatter={(v) => Number(v).toLocaleString()}
              />
            </Col>
            <Col span={12}>
              <Statistic
                title={<span style={{ color: TEXT_SUB, fontSize: 11 }}>近 1h 巨鲸告警</span>}
                value={summary.whaleAlertCount1h}
                valueStyle={{ color: summary.whaleAlertCount1h > 0 ? DOWN_COLOR : TEXT_SUB, fontSize: 14 }}
                suffix="次"
              />
            </Col>
            <Col span={12}>
              <Statistic
                title={<span style={{ color: TEXT_SUB, fontSize: 11 }}>24h 风控触发</span>}
                value={summary.riskTriggerCount24h}
                valueStyle={{ color: summary.riskTriggerCount24h > 0 ? '#faad14' : TEXT_SUB, fontSize: 14 }}
                suffix="次"
              />
            </Col>
          </Row>
        </>
      )}
    </div>
  )
}
