<script setup lang="ts">
import { ref } from 'vue'

const loading = ref(false)
const logs = ref<Array<{ id: number; operator: string; action: string; target: string; time: string; ip: string }>>([])
const total = ref(0)

// Placeholder page - backend would need an operation log API
function fetchLogs() {
  // TODO: Implement when backend API is available
}
</script>

<template>
  <div class="space-y-4 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">操作日志</h1>
      <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">查看系统操作记录</p>
    </div>

    <div class="card">
      <el-table :data="logs" v-loading="loading" size="small" stripe class="w-full" empty-text="暂无操作日志">
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="action" label="操作" min-width="150" />
        <el-table-column prop="target" label="目标" min-width="200" show-overflow-tooltip />
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="ip" label="IP" width="130" />
      </el-table>
      <div class="flex justify-between items-center px-4 py-3 border-t border-slate-100 dark:border-zinc-800">
        <span class="text-xs text-slate-400">共 {{ total }} 条</span>
        <el-pagination :total="total" background small layout="prev, pager, next" />
      </div>
    </div>
  </div>
</template>
