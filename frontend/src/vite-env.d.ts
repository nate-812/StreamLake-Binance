/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ENABLE_WS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
