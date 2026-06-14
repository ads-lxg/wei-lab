<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-white dark:bg-zinc-950 p-8">
    <div class="text-center max-w-md">
      <p class="text-7xl font-bold text-slate-200 dark:text-zinc-700 mb-4">404</p>
      <h2 class="text-xl font-semibold text-slate-700 dark:text-zinc-200 mb-2">页面未找到</h2>
      <p class="text-sm text-slate-500 dark:text-zinc-400 mb-6">您访问的页面不存在或已被移除</p>
      <el-button type="primary" @click="router.replace('/dashboard')">返回首页</el-button>
    </div>
  </div>
</template>
