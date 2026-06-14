<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { listByFolder, deleteDocument, batchDeleteDocuments, moveDocument, batchMoveDocuments } from '@/api/document'
import { getFolderTree as fetchFolderTree } from '@/api/folder'
import type { LiteratureListItemVO, FolderTreeVO } from '@/types'

const loading = ref(false)
const documents = ref<LiteratureListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const selectedFolderId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
const folderTree = ref<FolderTreeVO[]>([])

// 移动对话框
const moveDialogVisible = ref(false)
const moveTargetFolderId = ref<number | null>(null)
const moveDocIds = ref<number[]>([])
const moving = ref(false)

async function fetchFolders() {
  try {
    folderTree.value = await fetchFolderTree()
  } catch { /* ignore */ }
}

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    }
    if (selectedFolderId.value != null) {
      params.folderId = selectedFolderId.value
    }
    const res = await listByFolder(params as any)
    documents.value = res.records
    total.value = res.total
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleFolderSelect(folderId: number | null) {
  selectedFolderId.value = folderId
  keyword.value = ''
  page.value = 1
  fetchData()
}

function handleSelectionChange(rows: LiteratureListItemVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除此文献？将移入回收站。', '删除文献', { type: 'warning' })
    await deleteDocument(id)
    ElMessage.success('已移入回收站')
    fetchData()
  } catch { /* cancelled */ }
}

async function handleBatchDelete() {
  if (!selectedIds.value.length) { ElMessage.warning('请先选择文献'); return }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 份文献？`, '批量删除', { type: 'warning' })
    await batchDeleteDocuments({ documentIds: selectedIds.value })
    ElMessage.success('已移入回收站')
    fetchData()
  } catch { /* cancelled */ }
}

function openMoveDialog(ids: number[]) {
  if (!ids.length) { ElMessage.warning('请先选择文献'); return }
  moveDocIds.value = ids
  moveTargetFolderId.value = null
  moveDialogVisible.value = true
}

async function handleMove() {
  if (!moveTargetFolderId.value) { ElMessage.warning('请选择目标目录'); return }
  moving.value = true
  try {
    if (moveDocIds.value.length === 1) {
      await moveDocument(moveDocIds.value[0], moveTargetFolderId.value)
    } else {
      await batchMoveDocuments({ documentIds: moveDocIds.value, targetFolderId: moveTargetFolderId.value })
    }
    ElMessage.success('移动成功')
    moveDialogVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('移动失败')
  } finally {
    moving.value = false
  }
}

// 扁平化目录树用于选择
const flatFolders = computed(() => {
  const result: { id: number; name: string; depth: number }[] = []
  function walk(nodes: FolderTreeVO[], depth = 0) {
    for (const node of nodes) {
      result.push({ id: node.id, name: node.folderName, depth })
      if (node.children?.length) walk(node.children, depth + 1)
    }
  }
  walk(folderTree.value)
  return result
})

onMounted(() => {
  fetchFolders()
  fetchData()
})
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold" style="color: var(--text-primary)">文献管理</h1>
      <p class="text-sm mt-1" style="color: var(--text-muted)">管理所有文献 — 查看、移动、删除</p>
    </div>

    <!-- 工具栏 -->
    <div class="card p-4 flex flex-wrap items-center gap-3">
      <el-input
        v-model="keyword"
        placeholder="搜索文献..."
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-select v-model="selectedFolderId" placeholder="按目录筛选" clearable style="width: 200px" @change="handleFolderSelect">
        <el-option :value="null" label="全部目录" />
        <el-option v-for="f in flatFolders" :key="f.id" :value="f.id" :label="'　'.repeat(f.depth) + f.name" />
      </el-select>

      <div class="flex-1" />

      <el-button :disabled="!selectedIds.length" @click="openMoveDialog(selectedIds)">
        <el-icon :size="14"><Rank /></el-icon> 批量移动 ({{ selectedIds.length }})
      </el-button>
      <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">
        <el-icon :size="14"><Delete /></el-icon> 批量删除 ({{ selectedIds.length }})
      </el-button>
    </div>

    <!-- 文献表格 -->
    <div class="card">
      <el-table :data="documents" v-loading="loading" @selection-change="handleSelectionChange" stripe>
        <el-table-column type="selection" width="45" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="color: var(--text-primary)">{{ row.title || row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="authors" label="作者" width="140" show-overflow-tooltip />
        <el-table-column prop="folderName" label="目录" width="120" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.fileType === 'pdf' ? 'danger' : row.fileType === 'docx' ? 'primary' : 'info'">
              {{ row.fileType?.toUpperCase() || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="parseStatus" label="解析" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.parseStatus === 'SUCCESS' ? 'success' : row.parseStatus === 'FAILED' ? 'danger' : 'warning'">
              {{ row.parseStatus === 'SUCCESS' ? '完成' : row.parseStatus === 'FAILED' ? '失败' : '处理中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="downloadCount" label="下载" width="70" align="center" />
        <el-table-column prop="createTime" label="上传时间" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="openMoveDialog([row.id])">移动</el-button>
            <el-popconfirm title="确认删除此文献？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" text type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="p-4 flex justify-end">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          small
          layout="total, sizes, prev, pager, next"
          @current-change="fetchData"
          @size-change="fetchData"
        />
      </div>
    </div>

    <!-- 移动对话框 -->
    <el-dialog v-model="moveDialogVisible" title="移动文献" width="400px">
      <p class="text-sm mb-3" style="color: var(--text-muted)">
        将 {{ moveDocIds.length }} 份文献移动到：
      </p>
      <el-tree
        :data="folderTree"
        node-key="id"
        :props="{ label: 'folderName', children: 'children' }"
        highlight-current
        default-expand-all
        @node-click="(data: FolderTreeVO) => moveTargetFolderId = data.id"
      >
        <template #default="{ data }">
          <div class="flex items-center gap-2" :class="{ 'font-bold': moveTargetFolderId === data.id }">
            <el-icon :size="14"><FolderOpened v-if="data.children?.length" /><Folder v-else /></el-icon>
            <span>{{ data.folderName }}</span>
          </div>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="moving" :disabled="!moveTargetFolderId" @click="handleMove">确定移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>
