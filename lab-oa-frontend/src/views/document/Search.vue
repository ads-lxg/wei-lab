<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { search as globalSearch } from '@/api/search'
import { searchDocuments } from '@/api/document'
import type { LiteratureListItemVO, SearchHit, SearchResult } from '@/types'

const router = useRouter()
const keyword = ref('')
const searchType = ref<'local' | 'global'>('local')
const loading = ref(false)

const localResults = ref<LiteratureListItemVO[]>([])
const localTotal = ref(0)
const globalResults = ref<SearchHit[]>([])
const globalTotal = ref(0)

const page = ref(1)
const size = ref(10)

async function doSearch() {
  if (!keyword.value.trim()) return
  loading.value = true
  try {
    if (searchType.value === 'local') {
      const result = await searchDocuments({
        keyword: keyword.value,
        page: page.value,
        size: size.value,
      })
      localResults.value = result.records
      localTotal.value = result.total
    } else {
      const result: SearchResult = await globalSearch(keyword.value, 'all', page.value, size.value)
      globalResults.value = result.hits
      globalTotal.value = result.total
    }
  } finally {
    loading.value = false
  }
}

function viewDetail(id: number) {
  router.push(`/document/detail/${id}`)
}

function highlightText(text: string): string {
  if (!keyword.value || !text) return text || ''
  const regex = new RegExp(`(${keyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return text.replace(regex, '<mark class="bg-yellow-200 dark:bg-yellow-800 rounded px-0.5">$1</mark>')
}
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">搜索文献</h1>
      <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">搜索平台中的所有文献</p>
    </div>

    <!-- Search Bar -->
    <div class="card p-4">
      <div class="flex items-center gap-3">
        <el-input
          v-model="keyword"
          placeholder="输入文件名、标题、作者或关键词..."
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
          <el-radio-button value="local">文献库搜索</el-radio-button>
          <el-radio-button value="global">全文搜索</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- Results -->
    <div v-loading="loading" class="space-y-4">
      <!-- Local results -->
      <template v-if="searchType === 'local'">
        <div v-if="localResults.length > 0" class="text-sm text-slate-400 mb-2">
          找到 {{ localTotal }} 条结果
        </div>
        <div
          v-for="doc in localResults" :key="doc.id"
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
        <div v-if="!loading && keyword && localResults.length === 0" class="text-center py-12 text-slate-400">
          未找到相关文献
        </div>
      </template>

      <!-- Global results -->
      <template v-if="searchType === 'global'">
        <div v-if="globalResults.length > 0" class="text-sm text-slate-400 mb-2">
          找到 {{ globalTotal }} 条结果
        </div>
        <div
          v-for="hit in globalResults" :key="hit.id"
          class="card p-4 cursor-pointer hover:border-primary-200 dark:hover:border-primary-800 transition-all"
          @click="hit.docId && viewDetail(hit.docId)"
        >
          <p class="text-sm font-medium text-primary-600 dark:text-primary-400">{{ hit.title || hit.fileName }}</p>
          <p class="text-xs text-slate-500 mt-1" v-html="highlightText((hit.content || '').substring(0, 200))" />
          <div class="flex items-center gap-2 mt-2 text-xs text-slate-400">
            <span class="badge badge-primary text-[10px]">{{ hit.docType }}</span>
            <span>相关性: {{ (hit.score * 100).toFixed(0) }}%</span>
          </div>
        </div>
        <div v-if="!loading && keyword && globalResults.length === 0" class="text-center py-12 text-slate-400">
          未找到相关结果
        </div>
      </template>

      <!-- Pagination -->
      <div v-if="(searchType === 'local' ? localTotal : globalTotal) > size" class="flex justify-center pt-4">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="searchType === 'local' ? localTotal : globalTotal"
          background
          layout="prev, pager, next"
          @current-change="doSearch"
        />
      </div>
    </div>
  </div>
</template>
