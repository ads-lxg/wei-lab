<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useConfigStore } from '@/stores/config'
import SidebarNav from '@/components/layout/SidebarNav.vue'
import Topbar from '@/components/layout/Topbar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const configStore = useConfigStore()

const sidebarWidth = computed(() =>
  appStore.sidebarCollapsed ? '64px' : '240px'
)

// 动态更新页面标题
watch(() => configStore.siteName, (name) => {
  const pageTitle = route.meta.title as string
  document.title = pageTitle ? `${pageTitle} - ${name}` : name
}, { immediate: true })

watch(() => route.meta.title, (title) => {
  document.title = title ? `${title} - ${configStore.siteName}` : configStore.siteName
})
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-white dark:bg-zinc-950">
    <!-- Sidebar -->
    <SidebarNav />
    
    <!-- Main Content -->
    <div class="flex flex-col flex-1 min-w-0 transition-all duration-300">
      <Topbar />
      
      <main class="flex-1 overflow-auto p-6">
        <router-view v-slot="{ Component, route: r }">
          <transition name="page-fade" mode="out-in">
            <keep-alive :include="['DocumentLibrary', 'RagChat', 'AdminUsers']">
              <component :is="Component" :key="r.fullPath" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.15s ease;
}
.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
