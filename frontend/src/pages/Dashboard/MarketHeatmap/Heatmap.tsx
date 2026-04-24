import { useEffect, useState } from 'react'
import ReactECharts from 'echarts-for-react'
import { Typography } from 'antd'
import { fetchMultiSummary } from '../../../api'
import { MarketSummary, SYMBOL_LIST } from '../../../store/marketStore'
import { TEXT_SUB } from '../../../theme/darkTheme'

function pctToColor(pct: number): string {
  const clamped = Math.max(-5, Math.min(5, pct))
  if (clamped >= 0) {
    const t = clamped / 5
    return `rgba(0, ${Math.round(80 + t * 128)}, ${Math.round(80 + t * 78)}, ${0.5 + t * 0.5})`
  } else {
    const t = -clamped / 5
    return `rgba(${Math.round(150 + t * 89)}, ${Math.round(50 - t * 30)}, ${Math.round(50 - t * 30)}, ${0.5 + t * 0.5})`
  }
}

export default function Heatmap() {
  const [data, setData] = useState<MarketSummary[]>([])

  async function load() {
    const res = await fetchMultiSummary(SYMBOL_LIST)
    setData(res)
  }

  useEffect(() => {
    load()
    const id = setInterval(load, 60_000)
    return () => clearInterval(id)
  }, [])

  const treeData = data
    .filter((s) => s.lastPrice != null && Number(s.quoteVolume24h) > 0)
    .map((s) => ({
      name:  s.symbol.replace('USDT', ''),
      value: Number(s.quoteVolume24h),
      pct:   Number(s.priceChangePct24h),
    }))

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      formatter: (p: any) => {
        const d = p.data
        const sign = d.pct >= 0 ? '+' : ''
        return `${d.name}<br/>涨跌幅：${sign}${Number(d.pct).toFixed(2)}%<br/>成交额：${Number(p.data.value).toLocaleString()} USDT`
      },
    },
    series: [{
      type:          'treemap',
      roam:          false,
      nodeClick:     false,
      breadcrumb:    { show: false },
      width:         '100%',
      height:        '100%',
      data:          treeData,
      itemStyle:     { borderWidth: 2, borderColor: '#0d1117' },
      label: {
        show:      true,
        formatter: (p: any) => {
          const sign = p.data.pct >= 0 ? '+' : ''
          return `{name|${p.data.name}}\n{pct|${sign}${Number(p.data.pct).toFixed(2)}%}`
        },
        rich: {
          name: { color: '#e6edf3', fontSize: 13, fontWeight: 600 },
          pct:  { color: '#e6edf3', fontSize: 11 },
        },
      },
      colorMappingBy: 'value',
      visibleMin:     100,
      // 每个节点颜色由 pct 决定，在 data 里指定 itemStyle
      levels: [{ itemStyle: { borderWidth: 0 } }],
    }],
  }

  // 给每个节点注入颜色
  if (option.series[0].data) {
    option.series[0].data = (option.series[0].data as any[]).map((d) => ({
      ...d,
      itemStyle: { color: pctToColor(d.pct), borderColor: '#0d1117', borderWidth: 2 },
    }))
  }

  return (
    <div>
      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Text style={{ color: TEXT_SUB, fontSize: 12 }}>
          24h 涨跌幅热力图（色块大小 = 成交额）
        </Typography.Text>
        <Typography.Text style={{ color: TEXT_SUB, fontSize: 11 }}>每 60s 刷新</Typography.Text>
      </div>
      {data.length > 0 ? (
        <ReactECharts option={option} style={{ height: 180 }} opts={{ renderer: 'canvas' }} />
      ) : (
        <div style={{ height: 180, display: 'flex', alignItems: 'center', justifyContent: 'center', color: TEXT_SUB, fontSize: 13 }}>
          加载热力图数据中…
        </div>
      )}
    </div>
  )
}
