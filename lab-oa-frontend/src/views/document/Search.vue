<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchDocuments } from '@/api/document'
import { escapeHtml } from '@/utils/sanitize'
import type { LiteratureListItemVO } from '@/types'

const router = useRouter()
const keyword = ref('')
const searchType = ref<'local' | 'global'>('local')
const loading = ref(false)

const results = ref<LiteratureListItemVO[]>([])
const total = ref(0)

const page = ref(1)
const size = ref(10)

async function doSearch() {
  if (!keyword.value.trim()) return
  loading.value = true
  try {
    const mode = searchType.value === 'local' ? 'bm25' : 'knn'
    const result = await searchDocuments({
      keyword: keyword.value,
      page: page.value,
      size: size.value,
      searchMode: mode,
    })
    results.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function viewDetail(id: number | string) {
  router.push(`/document/detail/${id}`)
}

function highlightText(text: string): string {
  if (!keyword.value || !text) return escapeHtml(text || '')
  const safeText = escapeHtml(text)
  const safeKeyword = keyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${safeKeyword})`, 'gi')
  return safeText.replace(regex, '<mark class="bg-yellow-200 dark:bg-yellow-800 rounded px-0.5">$1</mark>')
}
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">搜索文献</h1>
      <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">搜索平台中的所有文献，支持中文搜索英文文献</p>
    </div>

    <!-- Search Bar -->
    <div class="card p-4">
      <div class="flex items-center gap-3">
        <el-input
          v-model="keyword"
          placeholder="输入文件名、标题、作者、关键词或中文描述..."
          size="large"
          class="flex-1"
          :prefix-icon="Search"
          clearable
          @keyup.enter="doSearch"
        />
        <el-button type="primary" size="large" :loading="loading" @click="doSearch">
          <el-icon :size="16"><Search /></el-icon> 搜索
        </el-button>
      </div>
      <div class="flex items-center gap-4 mt-3">
        <el-radio-group v-model="searchType" size="small">
          <el-radio-button value="local">精确查询</el-radio-button>
          <el-radio-button value="global">模糊查询</el-radio-button>
        </el-radio-group>
      </div>
      <p class="text-xs text-slate-400 mt-2">
        <template v-if="searchType === 'local'">精确查询以BM25关键词匹配为主，精确度高，适合已知关键词的检索</template>
        <template v-else>模糊查询结合KNN语义检索与向量检索，支持中英文互搜，召回率更高</template>
      </p>
    </div>

    <!-- Results -->
    <div v-loading="loading" class="space-y-4">
      <div v-if="results.length > 0" class="text-sm text-slate-400 mb-2">
        找到 {{ total }} 条文献
      </div>
      <div
        v-for="doc in results" :key="doc.id"
        class="card p-4 cursor-pointer hover:border-primary-200 dark:hover:border-primary-800 transition-all"
        @click="viewDetail(doc.id)"
      >
        <div class="flex items-start gap-3">
          <div class="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
            <el-icon :size="18"><Document /></el-icon>
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-primary-600 dark:text-primary-400 truncate">{{ doc.title || doc.fileName }}</p>
            <p class="text-xs text-slate-400 mt-0.5">{{ doc.authors || '-' }} · {{ doc.folderName }} · {{ doc.fileType }}</p>
            <p class="text-xs text-slate-500 mt-1 line-clamp-2" v-if="doc.keywords" v-html="highlightText(doc.keywords)" />
          </div>
          <span class="text-xs text-slate-400">{{ doc.createTime?.substring(0, 10) }}</span>
        </div>
      </div>
      <div v-if="!loading && keyword && results.length === 0" class="text-center py-12 text-slate-400">
        未找到相关文献
      </div>

      <!-- Pagination -->
      <div v-if="total > size" class="flex justify-center pt-4">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          background
          layout="prev, pager, next"
          @current-change="doSearch"
        />
      </div>
    </div>
  </div>
</template>
