import type { AppConfig, ThemeMode } from '@/types'

const CONFIG_KEY = 'laboa_config'

const defaults: AppConfig = {
  theme: 'minimal' as ThemeMode,
  darkMode: false,
  sidebarCollapsed: false,
  language: 'zh-CN',
}

export function getConfig(): AppConfig {
  try {
    const stored = localStorage.getItem(CONFIG_KEY)
    return stored ? { ...defaults, ...JSON.parse(stored) } : { ...defaults }
  } catch {
    return { ...defaults }
  }
}

export function setConfig(partial: Partial<AppConfig>): AppConfig {
  const current = getConfig()
  const updated = { ...current, ...partial }
  localStorage.setItem(CONFIG_KEY, JSON.stringify(updated))
  return updated
}

export function applyTheme(theme: ThemeMode) {
  const html = document.documentElement
  // 移除旧主题类
  html.classList.remove('theme-minimal', 'theme-card', 'theme-anime', 'theme-zhuanti')
  // 添加新主题类
  html.classList.add(`theme-${theme}`)
}

export function applyDarkMode(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
}
