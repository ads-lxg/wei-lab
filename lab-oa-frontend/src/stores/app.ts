import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConfig, setConfig, applyTheme, applyDarkMode } from '@/utils/config'
import type { AppConfig, ThemeMode } from '@/types'

export const useAppStore = defineStore('app', () => {
  const config = ref<AppConfig>(getConfig())
  const sidebarCollapsed = ref(config.value.sidebarCollapsed)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    setConfig({ sidebarCollapsed: sidebarCollapsed.value })
  }

  function setTheme(theme: ThemeMode) {
    config.value.theme = theme
    setConfig({ theme })
    applyTheme(theme)
  }

  function toggleDarkMode() {
    const dark = !config.value.darkMode
    config.value.darkMode = dark
    setConfig({ darkMode: dark })
    applyDarkMode(dark)
  }

  function initTheme() {
    applyTheme(config.value.theme)
    applyDarkMode(config.value.darkMode)
  }

  return {
    config, sidebarCollapsed,
    toggleSidebar, setTheme, toggleDarkMode, initTheme,
  }
})
