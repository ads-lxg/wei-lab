<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { RegisterDTO } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const form = reactive<RegisterDTO>({ email: '', username: '', password: '', realName: '' })
const loading = ref(false)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string>('')

async function handleRegister() {
  if (!form.email || !form.username || !form.password) {
    ElMessage.warning('请填写必填项')
    return
  }
  loading.value = true
  try {
    await userStore.doRegister(form)
    // 注册后如果有头像文件，自动登录后上传
    if (avatarFile.value) {
      const { login } = await import('@/api/auth')
      const res = await login({ username: form.username, password: form.password })
      userStore.token = res.token
      userStore.user = res.user
      const { uploadAvatar } = await import('@/api/auth')
      await uploadAvatar(avatarFile.value)
      userStore.fetchUserInfo()
    }
    ElMessage.success('注册成功')
    router.replace('/login')
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleAvatarChange(f: any) {
  const file = f?.raw || f
  if (!file) return
  avatarFile.value = file
  const reader = new FileReader()
  reader.onload = (e) => { avatarPreview.value = e.target?.result as string }
  reader.readAsDataURL(file)
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-white dark:bg-zinc-950 p-8">
    <div class="w-full max-w-sm animate-slide-up">
      <div class="text-center mb-8">
        <svg class="w-12 h-12 mx-auto mb-4" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="8" fill="#1677FF"/>
          <path d="M7 10h18M7 16h14M7 22h10" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/>
        </svg>
        <h2 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">创建账号</h2>
        <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">注册账号以开始使用</p>
      </div>

      <el-form @submit.prevent="handleRegister" class="space-y-3">
        <!-- 头像上传 -->
        <div class="flex justify-center mb-2">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            :on-change="handleAvatarChange"
          >
            <el-avatar
              :size="72"
              :src="avatarPreview"
              class="cursor-pointer ring-4 ring-slate-100 dark:ring-zinc-800 hover:ring-primary-200 dark:hover:ring-primary-800 transition-all"
            >
              <el-icon :size="32" class="text-slate-300"><Camera /></el-icon>
            </el-avatar>
          </el-upload>
        </div>
        <p class="text-center text-xs text-slate-400 -mt-1">点击上传头像（可选）</p>

        <el-form-item><el-input v-model="form.email" placeholder="邮箱" size="large" type="email" :prefix-icon="Message" /></el-form-item>
        <el-form-item><el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" /></el-form-item>
        <el-form-item><el-input v-model="form.password" placeholder="密码 (不少于6位)" size="large" type="password" :prefix-icon="Lock" show-password /></el-form-item>
        <el-form-item><el-input v-model="form.realName" placeholder="真实姓名 (选填)" size="large" :prefix-icon="UserFilled" /></el-form-item>

        <el-button type="primary" size="large" class="w-full" :loading="loading" @click="handleRegister">
          {{ loading ? '注册中...' : '注 册' }}
        </el-button>
      </el-form>

      <div class="mt-6 text-center text-sm text-slate-500 dark:text-zinc-400">
        已有账号？
        <router-link to="/login" class="text-primary-500 hover:text-primary-600 font-medium">返回登录</router-link>
      </div>
    </div>
  </div>
</template>
