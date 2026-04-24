import { ConfigProvider } from 'antd'
import { darkTheme } from './theme/darkTheme'
import Dashboard from './pages/Dashboard'

export default function App() {
  return (
    <ConfigProvider theme={darkTheme}>
      <Dashboard />
    </ConfigProvider>
  )
}
