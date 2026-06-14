<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDocumentDetail, getDownloadUrl } from '@/api/document'
import { WarningFilled, Document, Reading, CollectionTag, Download, View, CopyDocument, ArrowLeft } from '@element-plus/icons-vue'
import type { LiteratureDetailVO } from '@/types'

const route = useRoute()
const router = useRouter()

const id = route.params.id as string
const loading = ref(true)
const doc = ref<LiteratureDetailVO | null>(null)
const loadError = ref(false)
const downloading = ref(false)
const pdfUrl = ref<string | null>(null)
const showPdf = ref(false)

const isPdf = computed(() => doc.value?.fileType?.toLowerCase() === 'pdf')
const canPreview = computed(() => isPdf.value || doc.value?.fileType?.toLowerCase() === 'md' || doc.value?.fileType?.toLowerCase() === 'txt')

async function fetchDetail() {
  loading.value = true
  try {
    doc.value = await getDocumentDetail(id)
    // If PDF, try to get preview URL
    if (isPdf.value) {
      try {
        const url = await getDownloadUrl(id)
        pdfUrl.value = url
        showPdf.value = true
      } catch { /* preview not critical */ }
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function handleDownload() {
  downloading.value = true
  try {
    const url = await getDownloadUrl(id)
    window.open(url, '_blank')
  } finally { downloading.value = false }
}

async function handleCopyCite() {
  if (!doc.value) return
  const cite = `${doc.value.authors || 'Unknown'}. ${doc.value.title || doc.value.fileName}. ${doc.value.sourceJournal || ''}${doc.value.doi ? ` DOI: ${doc.value.doi}` : ''}`
  await navigator.clipboard.writeText(cite)
  ElMessage.success('引用已复制')
}

onMounted(fetchDetail)
</script>

<template>
  <div class="max-w-5xl mx-auto space-y-6 animate-fade-in" v-loading="loading">
    <!-- Error -->
    <div v-if="loadError" class="text-center py-16">
      <el-icon :size="56" style="color: var(--text-muted)"><WarningFilled /></el-icon>
      <h2 class="text-xl font-semibold mt-4" style="color: var(--text-primary)">文献不存在或已被删除</h2>
      <p class="text-sm mt-2" style="color: var(--text-muted)">该文献可能已从系统中移除</p>
      <el-button type="primary" class="mt-6" @click="router.push('/document/library')">返回文献库</el-button>
    </div>

    <div v-else-if="doc" class="space-y-6">
      <!-- Header -->
      <div class="flex items-start gap-4">
        <div class="w-14 h-14 rounded-xl bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold" style="color: var(--text-primary)">{{ doc.title || doc.fileName }}</h1>
            <el-button size="small" text :icon="CopyDocument" @click="handleCopyCite" title="复制引用" style="color: var(--text-muted)" />
            <el-button size="small" text :icon="ArrowLeft" @click="router.push('/document/library')" title="返回文献库" style="color: var(--text-muted)" />
          </div>
          <div class="flex flex-wrap items-center gap-2 mt-2">
            <span class="text-sm" style="color: var(--text-secondary)">{{ doc.authors || '未知作者' }}</span>
            <span class="badge badge-primary">{{ doc.fileType }}</span>
            <span class="badge badge-success">{{ doc.folderName }}</span>
            <span v-if="doc.parseStatus" class="badge" :class="doc.parseStatus === 'SUCCESS' ? 'badge-success' : 'badge-warning'">{{ doc.parseStatus }}</span>
          </div>
        </div>
      </div>

      <!-- PDF Preview -->
      <div v-if="showPdf && pdfUrl" class="card overflow-hidden">
        <div class="flex items-center justify-between px-4 py-2 border-b" style="border-color: var(--border-color)">
          <span class="text-sm font-medium" style="color: var(--text-secondary)">预览</span>
          <el-button size="small" text @click="showPdf = false">收起预览</el-button>
        </div>
        <iframe :src="pdfUrl" class="w-full border-0" style="height: 600px" />
      </div>

      <!-- Content Grid -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left: Abstract + Keywords -->
        <div class="lg:col-span-2 space-y-4">
          <div class="card p-5">
            <h3 class="text-sm font-semibold mb-3 flex items-center gap-2" style="color: var(--text-secondary)">
              <el-icon :size="14"><Reading /></el-icon> 摘要
            </h3>
            <p class="text-sm leading-relaxed" style="color: var(--text-secondary)">{{ doc.abstractText || '暂无摘要' }}</p>
          </div>

          <div class="card p-5">
            <h3 class="text-sm font-semibold mb-3 flex items-center gap-2" style="color: var(--text-secondary)">
              <el-icon :size="14"><CollectionTag /></el-icon> 关键词
            </h3>
            <div class="flex flex-wrap gap-2">
              <template v-if="doc.keywords">
                <span v-for="kw in doc.keywords.split(',').filter(Boolean)" :key="kw" class="badge badge-primary">{{ kw.trim() }}</span>
              </template>
              <span v-else class="text-sm" style="color: var(--text-muted)">暂无关键词</span>
            </div>
          </div>
        </div>

        <!-- Right: Meta + Stats + Actions -->
        <div class="space-y-4">
          <div class="card p-5 space-y-3">
            <h3 class="text-sm font-semibold" style="color: var(--text-secondary)">文献信息</h3>
            <div v-for="item in [
              { label: '文件名', value: doc.fileName },
              { label: '来源期刊', value: doc.sourceJournal || '-' },
              { label: 'DOI', value: doc.doi || '-' },
              { label: '上传者', value: doc.uploaderName || '-' },
              { label: '上传时间', value: doc.createTime?.substring(0, 10) || '-' },
            ]" :key="item.label" class="flex justify-between items-center text-sm">
              <span style="color: var(--text-muted)">{{ item.label }}</span>
              <span class="max-w-[180px] truncate text-right" style="color: var(--text-secondary)">{{ item.value }}</span>
            </div>
          </div>

          <div class="card p-5 space-y-3">
            <h3 class="text-sm font-semibold" style="color: var(--text-secondary)">统计数据</h3>
            <div class="flex items-center justify-between text-sm">
              <span style="color: var(--text-muted)">下载次数</span>
              <span class="font-semibold" style="color: var(--text-primary)">{{ doc.downloadCount }}</span>
            </div>
            <div class="flex items-center justify-between text-sm">
              <span style="color: var(--text-muted)">浏览次数</span>
              <span class="font-semibold" style="color: var(--text-primary)">{{ doc.viewCount }}</span>
            </div>
          </div>

          <div class="flex gap-2">
            <el-button type="primary" :loading="downloading" @click="handleDownload" title="下载">
              <el-icon :size="14"><Download /></el-icon>
            </el-button>
            <el-button v-if="isPdf" @click="showPdf = !showPdf" :title="showPdf ? '关闭预览' : '预览'">
              <el-icon :size="14"><View /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
