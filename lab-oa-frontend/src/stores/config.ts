import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listConfigs } from '@/api/config'

export const useConfigStore = defineStore('config', () => {
  const siteName = ref('魏大鹏课题组文献阅读室')
  // 始终使用 /api/config/icon 端点获取图标（从 MinIO 代理，永不过期）
  const siteLogo = ref('/api/config/icon')
  const loaded = ref(false)

  async function loadConfig() {
    if (loaded.value) return
    try {
      const configs = await listConfigs()
      if (configs.site_name) siteName.value = configs.site_name
      // 如果有 site_icon_file_id 或 site_icon_base64，说明图标已配置
      if (configs.site_icon_file_id || configs.site_icon_base64) {
        siteLogo.value = '/api/config/icon'
      }
      loaded.value = true
    } catch {
      loaded.value = true
    }
  }

  function updateLocal(configs: Record<string, string>) {
    if (configs.site_name !== undefined) siteName.value = configs.site_name
    // 保存后刷新图标（加时间戳避免浏览器缓存）
    if (configs.site_icon_file_id !== undefined || configs.site_icon_base64 !== undefined) {
      siteLogo.value = `/api/config/icon?t=${Date.now()}`
    }
  }

  return { siteName, siteLogo, loaded, loadConfig, updateLocal }
})
