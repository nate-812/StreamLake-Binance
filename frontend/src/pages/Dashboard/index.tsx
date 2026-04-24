import { useEffect, useState } from 'react'
import { Layout, Select, Button, Row, Col, Space, Typography } from 'antd'
import { RobotOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useMarketStore, SYMBOL_LIST } from '../../store/marketStore'
import { fetchKlines, fetchAlerts, fetchSummary } from '../../api'
import { useRealtimeWS } from '../../hooks/useRealtimeWS'
import TradingViewChart from './KlinePanel/TradingViewChart'
import SummaryCard from './MarketSummary/SummaryCard'
import AlertFeed from './WhaleAlertPanel/AlertFeed'
import DiagnosisDrawer from './AiDiagnosis/DiagnosisDrawer'
import Heatmap from './MarketHeatmap/Heatmap'
import { BG_PAGE, BG_CARD, BORDER, TEXT_SUB } from '../../theme/darkTheme'

const { Header, Content } = Layout

export default function Dashboard() {
  const { symbol, setSymbol, setKlines, prependAlerts, setSummary } = useMarketStore()
  const [drawerOpen, setDrawerOpen] = useState(false)

  useRealtimeWS()

  // 切换交易对时重新加载数据
  useEffect(() => {
    fetchKlines(symbol).then(setKlines).catch(console.error)
    fetchAlerts(symbol).then(prependAlerts).catch(console.error)
    fetchSummary(symbol).then(setSummary).catch(console.error)
  }, [symbol])

  // 定时刷新行情摘要（30s）
  useEffect(() => {
    const id = setInterval(() => {
      fetchSummary(symbol).then(setSummary).catch(() => null)
    }, 30_000)
    return () => clearInterval(id)
  }, [symbol])

  return (
    <Layout style={{ height: '100vh', background: BG_PAGE, overflow: 'hidden' }}>
      {/* ── 顶部导航栏 ── */}
      <Header style={{
        background: BG_CARD,
        borderBottom: `1px solid ${BORDER}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px',
        height: 56,
        lineHeight: '56px',
      }}>
        <Space align="center">
          <ThunderboltOutlined style={{ color: '#00d09e', fontSize: 20 }} />
          <Typography.Text strong style={{ fontSize: 16, color: '#e6edf3', letterSpacing: 1 }}>
            StreamLake
          </Typography.Text>
          <Typography.Text style={{ color: TEXT_SUB, fontSize: 12 }}>实时量化大屏</Typography.Text>
        </Space>

        <Space>
          <Select
            value={symbol}
            onChange={setSymbol}
            style={{ width: 140 }}
            options={SYMBOL_LIST.map((s) => ({ label: s, value: s }))}
          />
          <Button
            type="primary"
            icon={<RobotOutlined />}
            onClick={() => setDrawerOpen(true)}
          >
            AI 诊断
          </Button>
        </Space>
      </Header>

      {/* ── 主内容区 ── */}
      <Content style={{
        height: 'calc(100vh - 56px)',
        padding: '12px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        overflow: 'hidden',
      }}>
        {/* 上半区：K线 + 右侧面板 */}
        <Row gutter={12} style={{ flex: '1 1 0', minHeight: 0 }}>
          <Col span={18} style={{ display: 'flex', flexDirection: 'column' }}>
            <div style={{
              flex: 1,
              background: BG_CARD,
              border: `1px solid ${BORDER}`,
              borderRadius: 8,
              overflow: 'hidden',
              minHeight: 0,
            }}>
              <TradingViewChart />
            </div>
          </Col>

          <Col span={6} style={{ display: 'flex', flexDirection: 'column', gap: 12, minHeight: 0 }}>
            <SummaryCard />
            <div style={{ flex: 1, minHeight: 0 }}>
              <AlertFeed />
            </div>
          </Col>
        </Row>

        {/* 下半区：热力图 */}
        <div style={{
          background: BG_CARD,
          border: `1px solid ${BORDER}`,
          borderRadius: 8,
          padding: 12,
          height: 170,
          flexShrink: 0,
          overflow: 'hidden',
        }}>
          <Heatmap />
        </div>
      </Content>

      <DiagnosisDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </Layout>
  )
}
