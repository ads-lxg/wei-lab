<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { useConfigStore } from '@/stores/config'
import { useUserStore } from '@/stores/user'
import { updateConfigs } from '@/api/config'
import type { ThemeMode } from '@/types'

const appStore = useAppStore()
const configStore = useConfigStore()
const userStore = useUserStore()
const config = computed(() => appStore.config)

const isAdmin = computed(() => userStore.roles.some(r => r.toUpperCase() === 'ADMIN'))
const siteNameInput = ref('')
const siteLogoInput = ref('')
const saving = ref(false)

onMounted(() => {
  siteNameInput.value = configStore.siteName
  siteLogoInput.value = configStore.siteLogo
})

async function handleSaveSiteConfig() {
  saving.value = true
  try {
    await updateConfigs({ site_name: siteNameInput.value, site_logo: siteLogoInput.value })
    configStore.updateLocal({ site_name: siteNameInput.value, site_logo: siteLogoInput.value })
    document.title = siteNameInput.value
    ElMessage.success('配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const themes: { value: ThemeMode; label: string; desc: string }[] = [
  { value: 'minimal', label: '简约风格', desc: '极简白底，大量留白' },
  { value: 'zhuanti', label: '篆体风格', desc: '古典书卷气，宋体楷书' },
  { value: 'anime', label: '动漫风格', desc: '柔和的渐变与圆角' },
]
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">系统设置</h1>
      <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">自定义您的使用体验</p>
    </div>

    <!-- Site Config (Admin only) -->
    <div v-if="isAdmin" class="card p-5">
      <h3 class="text-sm font-semibold text-slate-700 dark:text-zinc-200 mb-4">站点配置</h3>
      <div class="space-y-4">
        <div>
          <label class="text-sm font-medium text-slate-600 dark:text-zinc-300 block mb-1">网站名称</label>
          <el-input v-model="siteNameInput" placeholder="输入网站名称" />
        </div>
        <div>
          <label class="text-sm font-medium text-slate-600 dark:text-zinc-300 block mb-1">网站图标路径</label>
          <el-input v-model="siteLogoInput" placeholder="输入图标URL或路径，如 /title.png" />
          <p class="text-xs text-slate-400 mt-1">支持相对路径（/xxx.png）或完整URL，图片文件请放到 public 目录下</p>
        </div>
        <div class="flex items-center gap-3">
          <div v-if="siteLogoInput" class="w-10 h-10 rounded border border-slate-200 dark:border-zinc-700 overflow-hidden">
            <img :src="siteLogoInput" alt="Preview" class="w-full h-full object-cover" />
          </div>
          <el-button type="primary" size="small" :loading="saving" @click="handleSaveSiteConfig">保存配置</el-button>
        </div>
      </div>
    </div>

    <!-- Appearance -->
    <div class="card p-5">
      <h3 class="text-sm font-semibold text-slate-700 dark:text-zinc-200 mb-4">外观</h3>

      <div class="space-y-4">
        <!-- Dark Mode -->
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-slate-600 dark:text-zinc-300">暗黑模式</p>
            <p class="text-xs text-slate-400">切换深色 / 浅色主题</p>
          </div>
          <el-switch :model-value="config.darkMode" @change="appStore.toggleDarkMode()" />
        </div>

        <div class="border-t border-slate-100 dark:border-zinc-800" />

        <!-- Theme Style -->
        <div>
          <p class="text-sm font-medium text-slate-600 dark:text-zinc-300 mb-3">界面风格</p>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div
              v-for="theme in themes" :key="theme.value"
              class="p-4 rounded-xl border-2 cursor-pointer transition-all"
              :class="config.theme === theme.value
                ? 'border-primary-500 bg-primary-50/50 dark:bg-primary-500/10'
                : 'border-slate-100 dark:border-zinc-800 hover:border-slate-200 dark:hover:border-zinc-700'"
              @click="appStore.setTheme(theme.value)"
            >
              <p class="text-sm font-medium text-slate-700 dark:text-zinc-200">{{ theme.label }}</p>
              <p class="text-xs text-slate-400 mt-0.5">{{ theme.desc }}</p>
              <div v-if="config.theme === theme.value" class="mt-2">
                <el-icon :size="14" class="text-primary-500"><CircleCheckFilled /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Language -->
    <div class="card p-5">
      <h3 class="text-sm font-semibold text-slate-700 dark:text-zinc-200 mb-4">语言</h3>
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm font-medium text-slate-600 dark:text-zinc-300">界面语言</p>
          <p class="text-xs text-slate-400">当前仅支持中文</p>
        </div>
        <el-select :model-value="config.language" disabled size="small" style="width: 140px">
          <el-option label="简体中文" value="zh-CN" />
          <el-option label="English" value="en-US" />
        </el-select>
      </div>
    </div>

    <!-- About -->
    <div class="card p-5">
      <h3 class="text-sm font-semibold text-slate-700 dark:text-zinc-200 mb-4">关于</h3>
      <div class="space-y-2 text-sm text-slate-500 dark:text-zinc-400">
        <div class="flex justify-between"><span>系统名称</span><span class="text-slate-700 dark:text-zinc-200">{{ configStore.siteName }}</span></div>
        <div class="flex justify-between"><span>版本号</span><span class="text-slate-700 dark:text-zinc-200">v1.0.0</span></div>
        <div class="flex justify-between"><span>技术栈</span><span class="text-slate-700 dark:text-zinc-200">Vue3 + Vite + Element Plus</span></div>
      </div>
    </div>
  </div>
</template>
