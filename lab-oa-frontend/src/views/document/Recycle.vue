<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listRecycleBin, recoverDocument, batchRecoverDocuments, permanentDeleteDocument, batchPermanentDeleteDocuments } from '@/api/document'
import { ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useFolderStore } from '@/stores/folder'
import type { LiteratureRecycleVO, PageResult } from '@/types'

const folderStore = useFolderStore()

const loading = ref(false)
const list = ref<LiteratureRecycleVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const selectedIds = ref<(number | string)[]>([])

async function fetchData() {
  loading.value = true
  try {
    const result: PageResult<LiteratureRecycleVO> = await listRecycleBin(page.value, size.value)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function handleRecover(id: number | string) {
  await recoverDocument(id)
  ElMessage.success('恢复成功')
  folderStore.fetchTree()
  fetchData()
}

async function handleBatchRecover() {
  if (selectedIds.value.length === 0) return
  await batchRecoverDocuments({ documentIds: selectedIds.value as number[] })
  ElMessage.success('批量恢复成功')
  selectedIds.value = []
  folderStore.fetchTree()
  fetchData()
}

/** 彻底删除 — DELETE /api/document/{id}/permanent */
async function handlePermanentDelete(id: number | string) {
  try {
    await permanentDeleteDocument(id)
    ElMessage.success('已彻底删除')
    fetchData()
  } catch {
    ElMessage.error('删除失败，文献不存在或已被删除')
  }
}

async function handleBatchPermanentDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(`永久删除 ${selectedIds.value.length} 篇文献后将无法恢复，确定吗？`, '批量永久删除', { type: 'error' })
    await batchPermanentDeleteDocuments({ documentIds: selectedIds.value as number[] })
    ElMessage.success('已彻底删除')
    selectedIds.value = []
    fetchData()
  } catch { /* cancelled */ }
}

function handleSelection(rows: LiteratureRecycleVO[]) {
  selectedIds.value = rows.map((r) => r.id)
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--text-primary)">回收站</h1>
        <p class="text-sm mt-1" style="color: var(--text-muted)">管理已删除的文献 — 可恢复或彻底删除</p>
      </div>
      <div class="flex gap-2">
        <el-button v-if="selectedIds.length > 0" type="primary" @click="handleBatchRecover">
          批量恢复 ({{ selectedIds.length }})
        </el-button>
        <el-button v-if="selectedIds.length > 0" type="danger" @click="handleBatchPermanentDelete">
          彻底删除 ({{ selectedIds.length }})
        </el-button>
        <el-button :icon="Refresh" @click="fetchData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <div class="card">
      <el-table :data="list" v-loading="loading" size="small" @selection-change="handleSelection" row-key="id" style="width: 100%">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="authors" label="作者" width="120" show-overflow-tooltip />
        <el-table-column prop="folderName" label="原目录" width="100" />
        <el-table-column prop="deleteTime" label="删除时间" width="150">
          <template #default="{ row }">{{ row.deleteTime?.substring(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleRecover(row.id)">恢复</el-button>
            <el-popconfirm title="永久删除后无法恢复！" @confirm="handlePermanentDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger" link>彻底删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-between items-center px-4 py-3 border-t" style="border-color: var(--border-color)">
        <span class="text-xs" style="color: var(--text-muted)">共 {{ total }} 条</span>
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" background small layout="prev, pager, next" @current-change="fetchData" />
      </div>
    </div>
  </div>
</template>
