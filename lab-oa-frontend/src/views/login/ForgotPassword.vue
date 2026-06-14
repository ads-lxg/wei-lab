<script setup lang="ts">
import { ref } from 'vue'

const email = ref('')
const loading = ref(false)

async function handleReset() {
  if (!email.value) { ElMessage.warning('请输入邮箱'); return }
  loading.value = true
  try {
    ElMessage.success('重置密码邮件已发送，请检查邮箱')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-white dark:bg-zinc-950 p-8">
    <div class="w-full max-w-sm animate-slide-up">
      <div class="text-center mb-8">
        <h2 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">重置密码</h2>
        <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">输入您的邮箱以接收重置链接</p>
      </div>

      <el-form @submit.prevent="handleReset" class="space-y-4">
        <el-form-item><el-input v-model="email" placeholder="邮箱地址" size="large" type="email" :prefix-icon="Message" /></el-form-item>
        <el-button type="primary" size="large" class="w-full" :loading="loading" @click="handleReset">发送重置链接</el-button>
      </el-form>

      <div class="mt-6 text-center text-sm text-slate-500 dark:text-zinc-400">
        <router-link to="/login" class="text-primary-500 hover:text-primary-600 font-medium">返回登录</router-link>
      </div>
    </div>
  </div>
</template>
