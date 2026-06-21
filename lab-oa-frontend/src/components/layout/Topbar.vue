<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useAppStore } from '@/stores/app'
import { getNotifications, getUnreadCount, markAllAsRead } from '@/api/notification'
import { useAvatarUrl } from '@/hooks/useAvatar'
import type { Notification } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const userAvatarSrc = useAvatarUrl(computed(() => userStore.user?.avatar))

const unreadCount = ref(0)
const notifications = ref<Notification[]>([])
const showNotifications = ref(false)
const notificationLoading = ref(false)

async function fetchUnreadCount() {
  try {
    unreadCount.value = await getUnreadCount()
  } catch { /* ignore */ }
}
fetchUnreadCount()

async function toggleNotifications() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) {
    notificationLoading.value = true
    try {
      const res = await getNotifications(undefined, 1, 20)
      // 只显示未读通知
      notifications.value = res.records.filter((n: Notification) => !n.isRead)
    } finally {
      notificationLoading.value = false
    }
  }
}

async function handleMarkAllRead() {
  await markAllAsRead()
  unreadCount.value = 0
  showNotifications.value = false
}

function handleLogout() {
  userStore.logout()
  useChatStore().clearMessages()
  router.replace('/login')
}
</script>

<template>
  <header class="app-topbar flex items-center h-14 px-4 shrink-0">
    <!-- Left: Collapse toggle + breadcrumb -->
    <div class="flex items-center gap-3">
      <button class="btn-ghost !p-1.5" @click="appStore.toggleSidebar()">
        <el-icon :size="18">
          <Expand v-if="appStore.sidebarCollapsed" />
          <Fold v-else />
        </el-icon>
      </button>
      
      <div class="text-xs text-slate-400 dark:text-zinc-500 hidden sm:block">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            <el-icon :size="14"><Monitor /></el-icon>
          </el-breadcrumb-item>
          <el-breadcrumb-item v-if="$route.meta.title">
            {{ $route.meta.title }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
    </div>

    <div class="flex-1" />

    <!-- Right: Actions -->
    <div class="flex items-center gap-1">
      <!-- Theme Switcher -->
      <el-dropdown trigger="click" placement="bottom-end">
        <button class="btn-ghost !p-1.5" title="切换主题风格">
          <el-icon :size="18"><Brush /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="appStore.setTheme('minimal')">
              <el-icon :size="14"><Check v-if="appStore.config.theme === 'minimal'" /></el-icon>
              简约风格
            </el-dropdown-item>
            <el-dropdown-item @click="appStore.setTheme('zhuanti')">
              <el-icon :size="14"><Check v-if="appStore.config.theme === 'zhuanti'" /></el-icon>
              篆体风格
            </el-dropdown-item>
            <el-dropdown-item @click="appStore.setTheme('anime')">
              <el-icon :size="14"><Check v-if="appStore.config.theme === 'anime'" /></el-icon>
              动漫风格
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- Dark Mode Toggle -->
      <button class="btn-ghost !p-1.5" @click="appStore.toggleDarkMode()" :title="appStore.config.darkMode ? '切换亮色模式' : '切换暗黑模式'">
        <el-icon :size="18"><Moon v-if="appStore.config.darkMode" /><Sunny v-else /></el-icon>
      </button>

      <!-- Notifications -->
      <el-popover
        :visible="showNotifications"
        placement="bottom-end"
        :width="320"
        trigger="click"
        @hide="showNotifications = false"
      >
        <template #reference>
          <button class="btn-ghost !p-1.5 relative" @click="toggleNotifications()">
            <el-icon :size="18"><Bell /></el-icon>
            <span
              v-if="unreadCount > 0"
              class="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-[10px] rounded-full flex items-center justify-center font-bold"
            >{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </button>
        </template>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">通知</span>
            <el-button size="small" text @click="handleMarkAllRead" v-if="unreadCount > 0">全部已读</el-button>
          </div>
          <div v-if="notificationLoading" class="text-center py-4 text-sm text-slate-400">加载中...</div>
          <div v-else-if="notifications.length === 0" class="text-center py-4 text-sm text-slate-400">暂无通知</div>
          <div
            v-for="n in notifications"
            :key="n.id"
            class="flex items-start gap-2 p-2 rounded-lg hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors"
          >
            <div class="w-2 h-2 mt-1.5 rounded-full shrink-0" :class="n.isRead ? 'bg-slate-300' : 'bg-primary-500'" />
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium truncate">{{ n.title }}</div>
              <div class="text-xs text-slate-400 mt-0.5 line-clamp-2">{{ n.content }}</div>
            </div>
          </div>
        </div>
      </el-popover>

      <!-- User Menu -->
      <el-dropdown trigger="click" placement="bottom-end">
        <button class="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors ml-1">
          <el-avatar :size="28" :src="userAvatarSrc || userStore.user?.avatar">
            {{ (userStore.user?.realName || userStore.user?.username || 'U').charAt(0).toUpperCase() }}
          </el-avatar>
          <span class="text-sm font-medium text-slate-700 dark:text-zinc-200 hidden sm:inline">
            {{ userStore.user?.realName || userStore.user?.username }}
          </span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/profile')">
              <el-icon :size="14"><UserFilled /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item @click="router.push('/settings')">
              <el-icon :size="14"><Setting /></el-icon>
              系统设置
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon :size="14"><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>
