<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useConfigStore } from '@/stores/config'
import { useAvatarUrl } from '@/hooks/useAvatar'
import type { RouteRecordRaw } from 'vue-router'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()
const configStore = useConfigStore()
const sidebarAvatarSrc = useAvatarUrl(computed(() => userStore.user?.avatar))

interface MenuItem {
  path: string
  title: string
  icon: string
  children?: MenuItem[]
  perm?: string
  roles?: string[]
}

function hasAccess(item: MenuItem): boolean {
  // Admin menu visible to all, but non-admin items will be disabled
  return true
}

function hasRoleAccess(item: MenuItem): boolean {
  if (!item.roles || item.roles.length === 0) return true
  return item.roles.some(r => userStore.roles.some(ur => ur.toUpperCase() === r.toUpperCase()))
}

function isAdmin(): boolean {
  return userStore.roles.some(r => r.toUpperCase() === 'ADMIN')
}

// Build menu from router
const menuItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = []
  const mainRoute = router.getRoutes().find((r) => r.path === '/')
  if (!mainRoute?.children) return items

  const grouped: Record<string, MenuItem> = {}

  for (const child of mainRoute.children) {
    const meta = child.meta as Record<string, unknown>
    if (meta.hidden) continue
    if (!child.path) continue

    const item: MenuItem = {
      path: `/${child.path}`,
      title: (meta.title as string) || child.name as string,
      icon: (meta.icon as string) || 'Menu',
      perm: meta.perm as string | undefined,
      roles: meta.roles as string[] | undefined,
    }

    if (!hasAccess(item)) continue

    // Group by path prefix
    const prefix = child.path.split('/')[0]
    if (!grouped[prefix]) {
      grouped[prefix] = { path: '', title: '', icon: 'FolderOpened', children: [] }
    }
    grouped[prefix]?.children?.push(item)
  }

  // If only one item in group, flatten it
  const result: MenuItem[] = []
  for (const [key, group] of Object.entries(grouped)) {
    const children = group.children || []
    if (children.length === 1) {
      result.push(children[0])
    } else {
      result.push({
        path: '',
        title: groupTitle(key),
        icon: groupIcon(key),
        children,
      })
    }
  }

  return result
})

function groupTitle(key: string): string {
  const map: Record<string, string> = { document: '文献管理', knowledge: '知识问答', admin: '系统管理' }
  return map[key] || key
}
function groupIcon(key: string): string {
  const map: Record<string, string> = { document: 'FolderOpened', knowledge: 'MagicStick', admin: 'Setting' }
  return map[key] || 'Menu'
}

function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(path + '/')
}

function isGroupActive(children: MenuItem[]): boolean {
  return children.some((c) => isActive(c.path))
}

function go(path: string) {
  if (path) router.push(path)
}

onMounted(() => {
  configStore.loadConfig()
})
</script>

<template>
  <aside
    class="app-sidebar flex flex-col h-full transition-all duration-300 shrink-0"
    :class="[appStore.sidebarCollapsed ? 'w-[64px]' : 'w-[240px]']"
  >
    <!-- Logo -->
    <div class="flex items-center h-14 px-4 shrink-0 border-b border-slate-100 dark:border-zinc-800">
      <img :src="configStore.siteLogo" alt="Logo" class="w-7 h-7 shrink-0 rounded" />
      <span
        v-if="!appStore.sidebarCollapsed"
        class="ml-3 text-base font-semibold text-slate-800 dark:text-zinc-100 whitespace-nowrap"
      >{{ configStore.siteName }}</span>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 overflow-y-auto px-2 py-3 space-y-1">
      <template v-for="item in menuItems" :key="item.path || item.title">
        <!-- Group -->
        <template v-if="item.children && item.children.length > 1">
          <el-collapse-transition>
            <div v-show="!appStore.sidebarCollapsed">
              <div class="px-3 py-1.5 text-xs font-semibold text-slate-400 dark:text-zinc-500 uppercase tracking-wider">
                {{ item.title }}
              </div>
            </div>
          </el-collapse-transition>
          <a
            v-for="child in item.children"
            :key="child.path"
            class="sidebar-link"
            :class="{ active: isActive(child.path), disabled: !hasRoleAccess(child) }"
            :title="appStore.sidebarCollapsed ? child.title : ''"
            @click="!hasRoleAccess(child) ? $router.push('/403') : go(child.path)"
          >
            <el-icon :size="18"><component :is="child.icon" /></el-icon>
            <span v-if="!appStore.sidebarCollapsed" class="whitespace-nowrap">{{ child.title }}</span>
          </a>
        </template>
        <!-- Single item -->
        <a
          v-else-if="item.path"
          class="sidebar-link"
          :class="{ active: isActive(item.path), disabled: !hasRoleAccess(item) }"
          :title="appStore.sidebarCollapsed ? item.title : ''"
          @click="!hasRoleAccess(item) ? $router.push('/403') : go(item.path)"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span v-if="!appStore.sidebarCollapsed" class="whitespace-nowrap">{{ item.title }}</span>
        </a>
      </template>
    </nav>

    <!-- Bottom: User area -->
    <div class="p-2 border-t border-slate-100 dark:border-zinc-800">
      <div
        class="flex items-center gap-3 px-3 py-2 rounded-lg cursor-pointer hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors"
        @click="router.push('/profile')"
        :title="appStore.sidebarCollapsed ? userStore.user?.realName || userStore.user?.username : ''"
      >
        <el-avatar :size="28" :src="sidebarAvatarSrc || userStore.user?.avatar" class="shrink-0">
          {{ (userStore.user?.realName || userStore.user?.username || 'U').charAt(0).toUpperCase() }}
        </el-avatar>
        <div v-if="!appStore.sidebarCollapsed" class="flex-1 min-w-0">
          <div class="text-sm font-medium text-slate-700 dark:text-zinc-200 truncate">
            {{ userStore.user?.realName || userStore.user?.username }}
          </div>
          <div class="text-xs text-slate-400 dark:text-zinc-500 truncate">
            {{ userStore.user?.roles?.join(', ') || '' }}
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>
