<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getLogPage, getLogDetail, deleteLog, batchDeleteLogs } from '@/api/log'
import type { SysLogVO, SysLogSearchDTO } from '@/types'
import {
  Search, Refresh, Delete, View, InfoFilled, CircleCheckFilled, CircleCloseFilled, Document
} from '@element-plus/icons-vue'

const loading = ref(false)
const logs = ref<SysLogVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const selectedIds = ref<number[]>([])

// Search form
const searchForm = ref<SysLogSearchDTO>({
  page: 1,
  size: 10,
})

// Detail dialog
const detailVisible = ref(false)
const detailLog = ref<SysLogVO | null>(null)
const moduleOptions = ref([
  { label: '文献管理', value: '文献管理' },
  { label: '用户管理', value: '用户管理' },
  { label: '角色管理', value: '角色管理' },
  { label: '权限管理', value: '权限管理' },
  { label: '系统配置', value: '系统配置' },
  { label: '目录管理', value: '目录管理' },
  { label: '文件管理', value: '文件管理' },
  { label: '知识库', value: '知识库' },
  { label: '认证登录', value: '认证登录' },
])
const resultOptions = ref([
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAIL' },
])

// Date range shortcut
const dateRange = ref<[string, string] | null>(null)
const dateShortcuts = [
  { text: '今天', value: () => { const d = new Date(); const s = formatDate(d); return [s, s] as [string, string] } },
  { text: '最近7天', value: () => { const e = new Date(); const s = new Date(); s.setDate(s.getDate() - 7); return [formatDate(s), formatDate(e)] as [string, string] } },
  { text: '最近30天', value: () => { const e = new Date(); const s = new Date(); s.setDate(s.getDate() - 30); return [formatDate(s), formatDate(e)] as [string, string] } },
]

function formatDate(d: Date): string {
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

async function fetchLogs() {
  loading.value = true
  try {
    const params: SysLogSearchDTO = {
      ...searchForm.value,
      page: page.value,
      size: size.value,
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0] + ' 00:00:00'
      params.endTime = dateRange.value[1] + ' 23:59:59'
    }
    const result = await getLogPage(params)
    logs.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchLogs()
}

function handleReset() {
  searchForm.value = { page: 1, size: 10 }
  dateRange.value = null
  page.value = 1
  fetchLogs()
}

function handlePageChange(p: number) {
  page.value = p
  fetchLogs()
}

async function handleView(row: SysLogVO) {
  try {
    detailLog.value = await getLogDetail(row.id)
    detailVisible.value = true
  } catch { /* ignore */ }
}

async function handleDelete(row: SysLogVO) {
  await ElMessageBox.confirm('确定删除该操作日志？', '确认删除', { type: 'warning' })
  await deleteLog(row.id)
  ElMessage.success('删除成功')
  fetchLogs()
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择日志')
    return
  }
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条日志？`, '批量删除', { type: 'warning' })
  await batchDeleteLogs(selectedIds.value)
  ElMessage.success('删除成功')
  selectedIds.value = []
  fetchLogs()
}

function handleSelectionChange(rows: SysLogVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

onMounted(fetchLogs)
</script>

<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">操作日志</h1>
        <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">查看系统操作记录，支持多关键词筛选</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete" :icon="Delete" plain>批量删除</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
      </div>
    </div>

    <!-- Search Bar -->
    <div class="card">
      <el-form :model="searchForm" inline class="flex flex-wrap gap-y-2">
        <el-form-item label="操作人">
          <el-input v-model="searchForm.operator" placeholder="用户名/姓名" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-input v-model="searchForm.action" placeholder="如: 新增、删除" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="操作目标">
          <el-input v-model="searchForm.target" placeholder="目标名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="searchForm.module" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="m in moduleOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="searchForm.result" placeholder="全部" clearable style="width: 100px">
            <el-option v-for="r in resultOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="IP">
          <el-input v-model="searchForm.ip" placeholder="IP 地址" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :shortcuts="dateShortcuts"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
      </el-form>
    </div>

    <!-- Table -->
    <div class="card">
      <el-table
        :data="logs"
        v-loading="loading"
        size="small"
        stripe
        class="w-full"
        empty-text="暂无操作日志"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column prop="username" label="操作人" width="110">
          <template #default="{ row }">
            <span class="text-sm">{{ row.realName || row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="action" label="操作" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="目标" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="flex items-center gap-1">
              <el-icon :size="14" style="color: var(--text-muted)"><Document /></el-icon>
              <span class="text-sm truncate">{{ row.target || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tooltip :content="row.result === 'SUCCESS' ? '成功' : '失败'" placement="top">
              <el-icon :size="18" :style="{ color: row.result === 'SUCCESS' ? 'var(--success-color, #22c55e)' : 'var(--danger-color, #ef4444)' }">
                <CircleCheckFilled v-if="row.result === 'SUCCESS'" />
                <CircleCloseFilled v-else />
              </el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时" width="80" align="center">
          <template #default="{ row }">
            <span class="text-xs" :style="{ color: row.costTime > 1000 ? 'var(--danger-color, #ef4444)' : 'var(--text-muted)' }">
              {{ row.costTime != null ? row.costTime + 'ms' : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link :icon="View" @click="handleView(row)">详情</el-button>
            <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-between items-center px-4 py-3 border-t border-slate-100 dark:border-zinc-800">
        <span class="text-xs text-slate-400">共 {{ total }} 条</span>
        <el-pagination
          :total="total"
          :page-size="size"
          v-model:current-page="page"
          background
          small
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="640px" destroy-on-close>
      <template v-if="detailLog">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="操作人">{{ detailLog.realName }} ({{ detailLog.username }})</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detailLog.userId }}</el-descriptions-item>
          <el-descriptions-item label="模块">{{ detailLog.module }}</el-descriptions-item>
          <el-descriptions-item label="操作类型">
            <el-tag size="small" effect="plain">{{ detailLog.action }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作目标" :span="2">{{ detailLog.target || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求方法">{{ detailLog.requestMethod }}</el-descriptions-item>
          <el-descriptions-item label="目标ID">{{ detailLog.targetId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求URL" :span="2">
            <code class="text-xs bg-gray-100 dark:bg-zinc-800 px-2 py-0.5 rounded">{{ detailLog.requestUrl }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag :type="detailLog.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ detailLog.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detailLog.costTime != null ? detailLog.costTime + 'ms' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ detailLog.ip }}</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ detailLog.createTime }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>
