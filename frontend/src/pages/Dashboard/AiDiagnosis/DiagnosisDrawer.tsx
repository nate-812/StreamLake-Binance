import { useState } from 'react'
import { Drawer, Button, Spin, Alert, Typography, Divider } from 'antd'
import { RobotOutlined, ReloadOutlined } from '@ant-design/icons'
import ReactMarkdown from 'react-markdown'
import { useMarketStore } from '../../../store/marketStore'
import { fetchDiagnosis } from '../../../api'
import { TEXT_SUB } from '../../../theme/darkTheme'

interface Props {
  open:    boolean
  onClose: () => void
}

export default function DiagnosisDrawer({ open, onClose }: Props) {
  const symbol = useMarketStore((s) => s.symbol)
  const [loading,  setLoading]  = useState(false)
  const [report,   setReport]   = useState<string | null>(null)
  const [error,    setError]    = useState<string | null>(null)
  const [diagSym,  setDiagSym]  = useState('')

  async function runDiagnosis() {
    setLoading(true)
    setError(null)
    setReport(null)
    setDiagSym(symbol)
    try {
      const data = await fetchDiagnosis(symbol)
      setReport(data.reportMarkdown)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  function handleOpen() {
    // 打开抽屉且当前无报告时自动触发
    if (!report && !loading) runDiagnosis()
  }

  return (
    <Drawer
      title={
        <span>
          <RobotOutlined style={{ color: '#00d09e', marginRight: 8 }} />
          AI 市场诊断
        </span>
      }
      placement="right"
      width={520}
      open={open}
      onClose={onClose}
      afterOpenChange={(vis) => vis && handleOpen()}
      extra={
        <Button
          icon={<ReloadOutlined />}
          size="small"
          onClick={runDiagnosis}
          loading={loading}
          disabled={loading}
        >
          重新生成
        </Button>
      }
    >
      {/* 当前分析标的 */}
      {diagSym && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          分析标的：<strong style={{ color: '#e6edf3' }}>{diagSym}</strong>
        </Typography.Text>
      )}

      <Divider style={{ margin: '12px 0' }} />

      {/* 加载中 */}
      {loading && (
        <div style={{ textAlign: 'center', padding: 60 }}>
          <Spin size="large" />
          <div style={{ color: TEXT_SUB, marginTop: 16, fontSize: 13 }}>
            正在调用 DeepSeek 分析市场数据，请稍候…
          </div>
        </div>
      )}

      {/* 错误 */}
      {!loading && error && (
        <Alert
          type="error"
          message="诊断失败"
          description={error}
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* 报告正文（Markdown 渲染）*/}
      {!loading && report && (
        <div style={{
          fontSize: 14,
          lineHeight: 1.8,
          color: '#e6edf3',
        }}>
          <ReactMarkdown
            components={{
              h1: ({ children }) => <h1 style={{ color: '#00d09e', fontSize: 18, margin: '16px 0 8px' }}>{children}</h1>,
              h2: ({ children }) => <h2 style={{ color: '#e6edf3', fontSize: 15, margin: '14px 0 6px', borderBottom: '1px solid #30363d', paddingBottom: 4 }}>{children}</h2>,
              h3: ({ children }) => <h3 style={{ color: '#8b949e', fontSize: 13, margin: '10px 0 4px' }}>{children}</h3>,
              p:  ({ children }) => <p  style={{ color: '#c9d1d9', margin: '6px 0' }}>{children}</p>,
              li: ({ children }) => <li style={{ color: '#c9d1d9', margin: '3px 0' }}>{children}</li>,
              strong: ({ children }) => <strong style={{ color: '#e6edf3' }}>{children}</strong>,
              code: ({ children }) => (
                <code style={{ background: '#21262d', padding: '2px 6px', borderRadius: 4, fontSize: 12, color: '#79c0ff' }}>
                  {children}
                </code>
              ),
              blockquote: ({ children }) => (
                <blockquote style={{ borderLeft: '3px solid #00d09e', margin: '8px 0', paddingLeft: 12, color: '#8b949e' }}>
                  {children}
                </blockquote>
              ),
              hr: () => <hr style={{ borderColor: '#30363d', margin: '12px 0' }} />,
            }}
          >
            {report}
          </ReactMarkdown>
        </div>
      )}
    </Drawer>
  )
}
