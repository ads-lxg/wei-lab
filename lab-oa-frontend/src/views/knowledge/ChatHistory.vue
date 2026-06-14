<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { listSessions as fetchSessions, deleteSession, batchDeleteSessions, searchSessions as apiSearchSessions } from '@/api/chat'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Delete, Search, Plus, ChatLineSquare } from '@element-plus/icons-vue'

const router = useRouter()
const chatStore = useChatStore()
const loading = ref(false)
const sessions = ref<Array<{ sessionId: string; title: string; createTime: string; updateTime: string }>>([])
const searchKeyword = ref('')
const selectedIds = ref<string[]>([])

async function loadSessions() {
  loading.value = true
  try {
    sessions.value = await fetchSessions()
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) { await loadSessions(); return }
  loading.value = true
  try {
    sessions.value = await apiSearchSessions(searchKeyword.value.trim())
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}

function openSession(sessionId: string) {
  chatStore.loadHistory(sessionId).then(() => {
    router.push('/knowledge/rag-chat')
  })
}

async function handleDelete(sessionId: string) {
  try {
    await ElMessageBox.confirm('确认删除该对话？', '删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteSession(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    selectedIds.value = selectedIds.value.filter(id => id !== sessionId)
    chatStore.sessionsDirty = true
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确认删除 ${selectedIds.value.length} 个对话？`, '批量删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await batchDeleteSessions(selectedIds.value)
    sessions.value = sessions.value.filter(s => !selectedIds.value.includes(s.sessionId))
    chatStore.sessionsDirty = true
    ElMessage.success(`已删除 ${selectedIds.value.length} 个对话`)
    selectedIds.value = []
  } catch { /* cancelled */ }
}

function toggleSelect(sessionId: string) {
  const idx = selectedIds.value.indexOf(sessionId)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(sessionId)
}

function toggleSelectAll() {
  if (selectedIds.value.length === sessions.value.length) {
    selectedIds.value = []
  } else {
    selectedIds.value = sessions.value.map(s => s.sessionId)
  }
}

onMounted(loadSessions)
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--text-primary)">对话历史</h1>
        <p class="text-sm mt-1" style="color: var(--text-muted)">查看和管理您的 RAG 对话记录</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button v-if="selectedIds.length > 0" type="danger" size="small" @click="handleBatchDelete">
          <el-icon :size="14"><Delete /></el-icon> 删除 ({{ selectedIds.length }})
        </el-button>
        <el-button type="primary" @click="router.push('/knowledge/rag-chat')">
          <el-icon :size="14"><Plus /></el-icon> 新对话
        </el-button>
      </div>
    </div>

    <!-- Search -->
    <div class="card p-3">
      <el-input v-model="searchKeyword" placeholder="搜索对话标题或内容..." clearable :prefix-icon="Search" @keyup.enter="handleSearch" @clear="loadSessions" style="max-width: 400px" />
    </div>

    <div v-loading="loading">
      <div v-if="sessions.length === 0 && !loading" class="card p-12 text-center">
        <el-icon :size="48" class="text-slate-200 dark:text-zinc-700 mb-4"><ChatLineSquare /></el-icon>
        <p class="text-sm" style="color: var(--text-muted)">暂无对话历史</p>
        <el-button type="primary" class="mt-4" @click="router.push('/knowledge/rag-chat')">开始新对话</el-button>
      </div>

      <div v-else class="space-y-2">
        <!-- Select all -->
        <div class="card p-2 flex items-center gap-3">
          <el-checkbox :model-value="selectedIds.length === sessions.length && sessions.length > 0" @change="toggleSelectAll" />
          <span class="text-xs" style="color: var(--text-muted)">全选 ({{ sessions.length }})</span>
        </div>

        <div
          v-for="session in sessions" :key="session.sessionId"
          class="card p-4 cursor-pointer hover:shadow-md transition-all flex items-center gap-4"
        >
          <el-checkbox :model-value="selectedIds.includes(session.sessionId)" @change="toggleSelect(session.sessionId)" @click.stop />
          <div class="flex-1 min-w-0" @click="openSession(session.sessionId)">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
                <el-icon :size="18"><ChatLineSquare /></el-icon>
              </div>
              <div class="min-w-0">
                <p class="text-sm font-medium truncate" style="color: var(--text-primary)">{{ session.title || '无标题对话' }}</p>
                <p class="text-xs mt-0.5" style="color: var(--text-muted)">{{ session.updateTime?.substring(0, 16) || session.createTime?.substring(0, 16) }}</p>
              </div>
            </div>
          </div>
          <el-button size="small" type="danger" text :icon="Delete" @click.stop="handleDelete(session.sessionId)" title="删除" />
        </div>
      </div>
    </div>
  </div>
</template>
