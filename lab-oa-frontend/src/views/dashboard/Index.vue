<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listByFolder, searchDocuments } from '@/api/document'
import { getFolderTree } from '@/api/folder'
import type { LiteratureListItemVO } from '@/types'

const router = useRouter()

const stats = ref({
  totalDocuments: 0,
  totalFolders: 0,
  totalDownloads: 0,
  todayNewDocuments: 0,
})

const recentUploads = ref<LiteratureListItemVO[]>([])
const hotDocuments = ref<LiteratureListItemVO[]>([])
const recentDownloads = ref<LiteratureListItemVO[]>([])
const loading = ref(true)

async function loadDashboard() {
  loading.value = true
  try {
    // Load folder count
    const tree = await getFolderTree()
    stats.value.totalFolders = countFolders(tree)

    // Load recent documents across all folders
    const result = await searchDocuments({ page: 1, size: 5, sortField: 'createTime', sortOrder: 'desc' })
    recentUploads.value = result.records

    // Calculate stats from recent uploads
    stats.value.totalDocuments = result.total
    stats.value.totalDownloads = recentUploads.value.reduce((sum, d) => sum + (d.downloadCount || 0), 0)

    // Hot documents
    const hot = await searchDocuments({ page: 1, size: 5, sortField: 'downloadCount', sortOrder: 'desc' })
    hotDocuments.value = hot.records

    // Simulated today count (backend doesn't have a direct API, use a rough estimate)
    stats.value.todayNewDocuments = recentUploads.value.filter(d => {
      if (!d.createTime) return false
      return d.createTime.startsWith(new Date().toISOString().slice(0, 10))
    }).length

    recentDownloads.value = hot.records.slice(0, 5)
  } catch {
    // Ignore errors on dashboard
  } finally {
    loading.value = false
  }
}

function countFolders(tree: any[]): number {
  let count = 0
  for (const node of tree) {
    count++
    if (node.children) count += countFolders(node.children)
  }
  return count
}

onMounted(loadDashboard)
</script>

<template>
  <div class="space-y-6 animate-fade-in" v-loading="loading">
    <!-- Page Header -->
    <div>
      <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">仪表盘</h1>
      <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">文献管理与知识平台的概览</p>
    </div>

    <!-- Stats Cards -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="card p-5">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs text-slate-500 dark:text-zinc-400 font-medium uppercase tracking-wider">总文献数</p>
            <p class="text-3xl font-bold text-slate-800 dark:text-zinc-100 mt-1">{{ stats.totalDocuments }}</p>
          </div>
          <div class="w-10 h-10 rounded-xl bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500">
            <el-icon :size="20"><Document /></el-icon>
          </div>
        </div>
      </div>

      <div class="card p-5">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs text-slate-500 dark:text-zinc-400 font-medium uppercase tracking-wider">目录数</p>
            <p class="text-3xl font-bold text-slate-800 dark:text-zinc-100 mt-1">{{ stats.totalFolders }}</p>
          </div>
          <div class="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-500/20 flex items-center justify-center text-emerald-500">
            <el-icon :size="20"><FolderOpened /></el-icon>
          </div>
        </div>
      </div>

      <div class="card p-5">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs text-slate-500 dark:text-zinc-400 font-medium uppercase tracking-wider">下载次数</p>
            <p class="text-3xl font-bold text-slate-800 dark:text-zinc-100 mt-1">{{ stats.totalDownloads }}</p>
          </div>
          <div class="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-500/20 flex items-center justify-center text-amber-500">
            <el-icon :size="20"><Download /></el-icon>
          </div>
        </div>
      </div>

      <div class="card p-5">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs text-slate-500 dark:text-zinc-400 font-medium uppercase tracking-wider">今日新增</p>
            <p class="text-3xl font-bold text-slate-800 dark:text-zinc-100 mt-1">{{ stats.todayNewDocuments }}</p>
          </div>
          <div class="w-10 h-10 rounded-xl bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500">
            <el-icon :size="20"><CirclePlus /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- Recent Activity + Hot Documents -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Recent Uploads -->
      <div class="card p-5">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-sm font-semibold text-slate-700 dark:text-zinc-200">最近上传</h3>
          <el-button size="small" text @click="router.push('/document/library')">查看全部</el-button>
        </div>
        <div v-if="recentUploads.length === 0" class="text-center py-8 text-sm text-slate-400">暂无数据</div>
        <div v-else class="space-y-3">
          <div
            v-for="doc in recentUploads" :key="doc.id"
            class="flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
            @click="router.push(`/document/detail/${doc.id}`)"
          >
            <div class="w-8 h-8 rounded-lg bg-slate-100 dark:bg-zinc-800 flex items-center justify-center text-slate-400 shrink-0">
              <el-icon :size="14"><Document /></el-icon>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-slate-700 dark:text-zinc-200 truncate">{{ doc.title || doc.fileName }}</p>
              <p class="text-xs text-slate-400 mt-0.5">{{ doc.authors || '-' }} · {{ doc.folderName }}</p>
            </div>
            <span class="badge badge-primary text-[10px]">{{ doc.fileType }}</span>
          </div>
        </div>
      </div>

      <!-- Hot Documents -->
      <div class="card p-5">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-sm font-semibold text-slate-700 dark:text-zinc-200">热门文献</h3>
          <el-button size="small" text @click="router.push('/document/search')">查看全部</el-button>
        </div>
        <div v-if="hotDocuments.length === 0" class="text-center py-8 text-sm text-slate-400">暂无数据</div>
        <div v-else class="space-y-3">
          <div
            v-for="(doc, idx) in hotDocuments" :key="doc.id"
            class="flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
            @click="router.push(`/document/detail/${doc.id}`)"
          >
            <span class="w-5 h-5 rounded-full bg-slate-100 dark:bg-zinc-800 flex items-center justify-center text-xs font-bold text-slate-500 shrink-0">
              {{ idx + 1 }}
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-slate-700 dark:text-zinc-200 truncate">{{ doc.title || doc.fileName }}</p>
              <p class="text-xs text-slate-400 mt-0.5">下载 {{ doc.downloadCount }} 次</p>
            </div>
            <el-button size="small" circle :icon="Download" @click.stop="router.push(`/document/detail/${doc.id}`)" />
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Actions -->
    <div class="card p-5">
      <h3 class="text-sm font-semibold text-slate-700 dark:text-zinc-200 mb-4">快捷操作</h3>
      <div class="flex flex-wrap gap-3">
        <el-button type="primary" @click="router.push('/document/upload')">
          <el-icon :size="14"><Upload /></el-icon> 上传文献
        </el-button>
        <el-button @click="router.push('/knowledge/rag-chat')">
          <el-icon :size="14"><ChatLineSquare /></el-icon> RAG 问答
        </el-button>
        <el-button @click="router.push('/document/search')">
          <el-icon :size="14"><Search /></el-icon> 搜索文献
        </el-button>
        <el-button @click="router.push('/document/library')">
          <el-icon :size="14"><FolderOpened /></el-icon> 浏览文献库
        </el-button>
      </div>
    </div>
  </div>
</template>
