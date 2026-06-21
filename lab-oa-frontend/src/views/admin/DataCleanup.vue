<script setup lang="ts">
import { ref } from 'vue'
import { executeDataCleanup } from '@/api/data-cleanup'

const loading = ref(false)
const result = ref<Record<string, number> | null>(null)
const errorMsg = ref('')

async function handleCleanup() {
  try {
    await ElMessageBox.confirm(
      '此操作将永久删除所有已删除超过10天的数据，包括用户、角色、文献、文档、文件记录等。\n\n确定要继续吗？',
      '确认数据清理',
      { type: 'warning', confirmButtonText: '确定清理', cancelButtonText: '取消' }
    )
  } catch { return }

  loading.value = true
  result.value = null
  errorMsg.value = ''
  try {
    result.value = await executeDataCleanup()
    const total = Object.values(result.value).reduce((a, b) => a + b, 0)
    if (total === 0) {
      ElMessage.success('没有需要清理的数据')
    } else {
      ElMessage.success(`清理完成，共删除 ${total} 条记录`)
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '清理失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold" style="color: var(--text-primary)">数据清理</h1>
      <p class="text-sm mt-1" style="color: var(--text-muted)">永久删除已逻辑删除且超过10天的冗余数据</p>
    </div>

    <!-- Info Card -->
    <div class="card p-5 space-y-3">
      <div class="flex items-start gap-3">
        <el-icon :size="20" style="color: #f59e0b"><WarningFilled /></el-icon>
        <div class="space-y-2 flex-1">
          <p class="text-sm font-medium" style="color: var(--text-primary)">说明</p>
          <ul class="text-xs space-y-1" style="color: var(--text-muted)">
            <li>系统每天凌晨3点自动清理已删除超过10天的数据</li>
            <li>手动清理将立即执行相同的清理逻辑</li>
            <li>清理范围：用户、角色、权限、题库、文献、目录、文档、对话、通知、文件记录</li>
            <li><strong style="color: #ef4444">此操作不可逆，清理前请确认</strong></li>
          </ul>
        </div>
      </div>

      <div class="flex items-center gap-3 pt-2">
        <el-button type="danger" :loading="loading" @click="handleCleanup">
          <el-icon :size="14"><Delete /></el-icon> 立即清理
        </el-button>
        <el-tag v-if="loading" type="warning" size="small">清理中...</el-tag>
      </div>

      <!-- Error -->
      <div v-if="errorMsg" class="text-sm p-3 rounded" style="color: #ef4444; background: rgba(239,68,68,0.08)">
        {{ errorMsg }}
      </div>

      <!-- Result -->
      <div v-if="result && Object.keys(result).length > 0" class="pt-2">
        <p class="text-sm font-medium mb-2" style="color: var(--text-primary)">清理结果</p>
        <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
          <div v-for="(count, table) in result" :key="table" class="flex items-center justify-between px-3 py-1.5 rounded text-xs" style="background: var(--bg-hover)">
            <code style="color: var(--text-secondary)">{{ table }}</code>
            <span class="font-medium" style="color: #ef4444">{{ count }} 条</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
