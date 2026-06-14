<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-white dark:bg-zinc-950 p-8">
    <div class="text-center max-w-md">
      <p class="text-7xl font-bold text-slate-200 dark:text-zinc-700 mb-4">403</p>
      <h2 class="text-xl font-semibold text-slate-700 dark:text-zinc-200 mb-2">访问被拒绝</h2>
      <p class="text-sm text-slate-500 dark:text-zinc-400 mb-6">您没有权限访问此页面，请联系管理员</p>
      <el-button type="primary" @click="router.replace('/dashboard')">返回首页</el-button>
    </div>
  </div>
</template>
