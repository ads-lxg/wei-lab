<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useConfigStore } from '@/stores/config'
import { User, Lock, Moon, Sunny } from '@element-plus/icons-vue'
import type { LoginDTO } from '@/types'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()
const configStore = useConfigStore()

const form = reactive<LoginDTO>({ username: '', password: '' })
const loading = ref(false)
const rememberMe = ref(false)

configStore.loadConfig()

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
    ElMessage.success('登录成功')
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex bg-white dark:bg-zinc-950">
    <!-- Left: Brand -->
    <div class="hidden lg:flex w-[38%] bg-gradient-to-br from-primary-500 via-primary-600 to-indigo-600 items-center justify-center relative overflow-hidden">
      <div class="absolute inset-0 opacity-10">
        <div class="absolute top-20 left-20 w-80 h-80 bg-white rounded-full blur-3xl" />
        <div class="absolute bottom-20 right-20 w-96 h-96 bg-white rounded-full blur-3xl" />
      </div>
      <div class="relative z-10 text-white text-center max-w-sm px-8">
        <img :src="configStore.siteLogo" alt="Logo" class="w-20 h-20 mx-auto mb-4 rounded-xl object-cover" />
        <h1 class="text-2xl font-bold mb-3 tracking-tight">{{ configStore.siteName }}</h1>
        <p class="text-sm text-white/70 leading-relaxed">
          文献管理与 RAG 智能问答平台
        </p>
        <div class="mt-6 flex gap-3 justify-center text-xs text-white/50">
          <span>AI 驱动</span>
          <span>·</span>
          <span>知识管理</span>
          <span>·</span>
          <span>高效科研</span>
        </div>
      </div>
    </div>

    <!-- Right: Login Form -->
    <div class="flex-1 flex items-center justify-center p-8">
      <div class="w-full max-w-sm animate-slide-up">
        <div class="lg:hidden mb-8 text-center">
          <img :src="configStore.siteLogo" alt="Logo" class="w-14 h-14 mx-auto mb-3 rounded-xl object-cover" />
          <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">{{ configStore.siteName }}</h1>
        </div>

        <h2 class="text-2xl font-bold text-slate-800 dark:text-zinc-100 mb-1">欢迎回来</h2>
        <p class="text-sm text-slate-500 dark:text-zinc-400 mb-8">登录您的账号以继续</p>

        <el-form @submit.prevent="handleLogin" class="space-y-4">
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="flex items-center justify-between">
            <el-checkbox v-model="rememberMe" size="small">记住登录</el-checkbox>
            <router-link to="/forgot-password" class="text-xs text-primary-500 hover:text-primary-600 transition-colors">
              忘记密码？
            </router-link>
          </div>

          <el-button
            type="primary"
            size="large"
            class="w-full"
            :loading="loading"
            :disabled="!form.username || !form.password"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form>

        <div class="mt-6 text-center text-sm text-slate-500 dark:text-zinc-400">
          还没有账号？
          <router-link to="/register" class="text-primary-500 hover:text-primary-600 font-medium transition-colors">
            立即注册
          </router-link>
        </div>

        <!-- Theme toggle -->
        <div class="mt-8 flex justify-center gap-4 text-slate-400">
          <button @click="appStore.toggleDarkMode()" class="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors">
            <el-icon :size="18"><Moon v-if="!appStore.config.darkMode" /><Sunny v-else /></el-icon>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
