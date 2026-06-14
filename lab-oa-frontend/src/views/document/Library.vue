<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFolderStore } from '@/stores/folder'
import { useDocumentStore } from '@/stores/document'
import { getDownloadUrl, batchDeleteDocuments } from '@/api/document'
import { FolderOpened, Folder, Upload, UploadFilled, Download, Delete, Refresh, List, Grid, View, Document } from '@element-plus/icons-vue'
import type { FolderTreeVO, LiteratureListItemVO } from '@/types'

const router = useRouter()
const folderStore = useFolderStore()
const docStore = useDocumentStore()

const viewMode = ref<'table' | 'card'>('table')
const selectedDocs = ref<LiteratureListItemVO[]>([])
const keyword = ref('')
const downloading = ref(false)
const downloadIds = ref<Set<number>>(new Set())

function handleNodeClick(data: FolderTreeVO) {
  folderStore.selectFolder(data.id)
  loadDocuments(data.id)
}

function loadDocuments(folderId: number) {
  docStore.fetchByFolder({
    folderId,
    page: docStore.currentPage,
    size: docStore.pageSize,
    keyword: keyword.value || undefined,
    sortField: 'createTime',
    sortOrder: 'desc',
  })
}

function handlePageChange(page: number) {
  if (folderStore.selectedFolderId) {
    docStore.fetchByFolder({
      folderId: folderStore.selectedFolderId,
      page,
      size: docStore.pageSize,
      keyword: keyword.value || undefined,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
  }
}

function handleSearch() {
  if (folderStore.selectedFolderId) loadDocuments(folderStore.selectedFolderId)
}

function viewDetail(id: number | string) {
  router.push(`/document/detail/${id}`)
}

function goUpload() {
  router.push({ path: '/document/upload', query: folderStore.selectedFolderId ? { folderId: String(folderStore.selectedFolderId) } : {} })
}

function goBatchUpload() {
  router.push({ path: '/document/batch-upload', query: folderStore.selectedFolderId ? { folderId: String(folderStore.selectedFolderId) } : {} })
}

function handleSelectionChange(rows: LiteratureListItemVO[]) {
  selectedDocs.value = rows
}

// ===== 下载单个 =====
async function handleDownload(doc: LiteratureListItemVO) {
  downloading.value = true
  downloadIds.value.add(doc.id)
  try {
    const url = await getDownloadUrl(doc.id)
    window.open(url, '_blank')
  } finally {
    downloading.value = false
    downloadIds.value.delete(doc.id)
  }
}

// ===== 批量下载 ZIP =====
function handleBatchDownload() {
  if (selectedDocs.value.length === 0) return
  const ids = selectedDocs.value.map((d) => d.id)
  // 构造 POST 请求直接触发浏览器下载
  const token = localStorage.getItem('laboa_token')
  fetch('/api/document/batch-download', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify({ documentIds: ids }),
  }).then(async (resp) => {
    if (!resp.ok) { ElMessage.error('下载失败'); return }
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'documents.zip'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('批量下载已开始')
  })
}

// ===== 批量删除 =====
async function handleBatchDelete() {
  if (selectedDocs.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedDocs.value.length} 篇文献？`, '批量删除', { type: 'warning' })
    await batchDeleteDocuments({ documentIds: selectedDocs.value.map((d) => d.id) })
    ElMessage.success('删除成功')
    selectedDocs.value = []
    if (folderStore.selectedFolderId) loadDocuments(folderStore.selectedFolderId)
  } catch { /* cancelled */ }
}

onMounted(async () => {
  await folderStore.fetchTree()
  if (folderStore.tree.length > 0) {
    const first = folderStore.tree[0]
    folderStore.selectFolder(first.id)
    loadDocuments(first.id)
  }
})
</script>

<template>
  <div class="flex gap-0 h-[calc(100vh-116px)] -m-6">
    <!-- Left: Folder Tree -->
    <div class="w-60 shrink-0 border-r p-3 overflow-auto" style="border-color: var(--border-color); background: var(--bg-sidebar)">
      <div class="flex items-center justify-between mb-3 px-1">
        <span class="text-xs font-semibold uppercase" style="color: var(--text-muted)">目录</span>
      </div>
      <el-tree
        :data="folderStore.tree"
        node-key="id"
        :props="{ label: 'folderName', children: 'children' }"
        :highlight-current="true"
        @node-click="handleNodeClick"
        default-expand-all
      >
        <template #default="{ data }">
          <span class="flex items-center gap-2 text-sm flex-1 truncate py-0.5">
            <el-icon :size="14"><FolderOpened v-if="data.children?.length" /><Folder v-else /></el-icon>
            <span>{{ data.folderName }}</span>
          </span>
        </template>
      </el-tree>
    </div>

    <!-- Right: Document List -->
    <div class="flex-1 flex flex-col min-w-0 overflow-hidden">
      <!-- Toolbar -->
      <div class="flex items-center gap-2 px-4 py-2 shrink-0" style="border-bottom: 1px solid var(--border-color)">
        <el-input
          v-model="keyword"
          placeholder="在当前目录中搜索..."
          size="small"
          class="max-w-[240px]"
          :prefix-icon="Search"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <div class="flex-1" />
        <el-button size="small" @click="goUpload"><el-icon :size="14"><Upload /></el-icon> 上传</el-button>
        <el-button size="small" @click="goBatchUpload"><el-icon :size="14"><UploadFilled /></el-icon> 批量上传</el-button>

        <template v-if="selectedDocs.length > 0">
          <el-tooltip content="批量下载 (ZIP)" placement="bottom">
            <el-button size="small" type="primary" plain @click="handleBatchDownload">
              <el-icon :size="14"><Download /></el-icon> ({{ selectedDocs.length }})
            </el-button>
          </el-tooltip>
          <el-tooltip content="批量删除" placement="bottom">
            <el-button size="small" type="danger" plain @click="handleBatchDelete">
              <el-icon :size="14"><Delete /></el-icon>
            </el-button>
          </el-tooltip>
        </template>
        <el-tooltip content="刷新" placement="bottom">
          <el-button size="small" :icon="Refresh" circle @click="folderStore.selectedFolderId && loadDocuments(folderStore.selectedFolderId)" />
        </el-tooltip>
        <el-button-group size="small">
          <el-button :type="viewMode === 'table' ? 'primary' : ''" @click="viewMode = 'table'"><el-icon :size="14"><List /></el-icon></el-button>
          <el-button :type="viewMode === 'card' ? 'primary' : ''" @click="viewMode = 'card'"><el-icon :size="14"><Grid /></el-icon></el-button>
        </el-button-group>
      </div>

      <!-- Table -->
      <div class="flex-1 overflow-auto p-4">
        <el-table
          v-if="viewMode === 'table'"
          :data="docStore.documents"
          v-loading="docStore.loading"
          @selection-change="handleSelectionChange"
          size="small"
          stripe
          row-key="id"
        >
          <el-table-column type="selection" width="40" />
          <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-sm text-primary-500 cursor-pointer hover:underline" @click.stop="viewDetail(row.id)">{{ row.fileName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
          <el-table-column prop="authors" label="作者" width="120" show-overflow-tooltip />
          <el-table-column prop="keywords" label="关键词" width="120" show-overflow-tooltip />
          <el-table-column prop="folderName" label="目录" width="100" />
          <el-table-column prop="fileType" label="类型" width="70" align="center">
            <template #default="{ row }"><span class="badge badge-primary text-[10px]">{{ row.fileType }}</span></template>
          </el-table-column>
          <el-table-column prop="downloadCount" label="下载" width="60" align="center" />
          <el-table-column prop="createTime" label="上传时间" width="120">
            <template #default="{ row }">{{ row.createTime?.substring(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" type="primary" link :icon="Download" :loading="downloadIds.has(row.id)" @click.stop="handleDownload(row)">下载</el-button>
              <el-button size="small" link :icon="View" @click.stop="viewDetail(row.id)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- Card Mode -->
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4" v-loading="docStore.loading">
          <div
            v-for="doc in docStore.documents" :key="doc.id"
            class="card p-4 cursor-pointer hover:shadow-md transition-all"
            @click="viewDetail(doc.id)"
          >
            <div class="flex items-start gap-3">
              <div class="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
                <el-icon :size="18"><Document /></el-icon>
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium truncate" style="color: var(--text-primary)">{{ doc.title || doc.fileName }}</p>
                <p class="text-xs mt-0.5" style="color: var(--text-muted)">{{ doc.authors || '-' }}</p>
              </div>
            </div>
            <div class="flex items-center gap-2 mt-3 text-xs" style="color: var(--text-muted)">
              <span class="badge badge-primary text-[10px]">{{ doc.fileType }}</span>
              <span>{{ doc.folderName }}</span>
              <span class="flex-1" />
              <span><el-icon :size="12"><Download /></el-icon> {{ doc.downloadCount }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div class="flex items-center justify-between px-4 py-2 shrink-0" style="border-top: 1px solid var(--border-color)">
        <span class="text-xs" style="color: var(--text-muted)">共 {{ docStore.total }} 条</span>
        <el-pagination
          v-model:current-page="docStore.currentPage"
          :page-size="docStore.pageSize"
          :total="docStore.total"
          background
          small
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>
