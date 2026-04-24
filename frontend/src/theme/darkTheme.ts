import { ThemeConfig, theme } from 'antd'

export const darkTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorBgBase:        '#0d1117',
    colorBgContainer:   '#161b22',
    colorBgElevated:    '#1c2128',
    colorBorder:        '#30363d',
    colorText:          '#e6edf3',
    colorTextSecondary: '#8b949e',
    colorPrimary:       '#00d09e',
    colorError:         '#ef5350',
    colorSuccess:       '#00d09e',
    borderRadius:       8,
  },
}

// 语义色（不依赖 Ant Design Token）
export const UP_COLOR   = '#00d09e'
export const DOWN_COLOR = '#ef5350'
export const BG_CARD    = '#161b22'
export const BG_PAGE    = '#0d1117'
export const BORDER     = '#30363d'
export const TEXT_MAIN  = '#e6edf3'
export const TEXT_SUB   = '#8b949e'
