import { ThemeConfig, theme } from 'antd'

export const darkTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorBgBase:        '#0B0E11',
    colorBgContainer:   '#1E2026',
    colorBgElevated:    '#2B3139',
    colorBorder:        '#2B3139',
    colorText:          '#EAECEF',
    colorTextSecondary: '#848E9C',
    colorPrimary:       '#F0B90B',
    colorError:         '#F6465D',
    colorSuccess:       '#0ECB81',
    borderRadius:       4,
    fontFamily:         `-apple-system, BlinkMacSystemFont, 'Inter', 'Segoe UI', sans-serif`,
  },
}

export const UP_COLOR   = '#0ECB81'
export const DOWN_COLOR = '#F6465D'
export const BG_CARD    = '#1E2026'
export const BG_PAGE    = '#0B0E11'
export const BORDER     = '#2B3139'
export const TEXT_MAIN  = '#EAECEF'
export const TEXT_SUB   = '#848E9C'
export const ACCENT     = '#F0B90B'
