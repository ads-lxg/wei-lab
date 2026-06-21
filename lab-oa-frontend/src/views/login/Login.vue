<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useConfigStore } from '@/stores/config'
import { checkCaptcha } from '@/api/auth'
import { User, Lock, Moon, Sunny, RefreshRight, View, Hide } from '@element-plus/icons-vue'
import type { LoginDTO } from '@/types'
import LoginCharacters from './LoginCharacters.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()
const configStore = useConfigStore()

const form = reactive<LoginDTO>({ username: '', password: '' })
const loading = ref(false)
const rememberMe = ref(false)
const showPassword = ref(false)
const isInputFocused = ref(false)
const isHoveringLogin = ref(false)
let hoverLoginTimer: ReturnType<typeof setTimeout> | null = null

function onLoginBtnMouseEnter() {
  if (hoverLoginTimer !== null) clearTimeout(hoverLoginTimer)
  hoverLoginTimer = setTimeout(() => {
    isHoveringLogin.value = true
  }, 600)
}

function onLoginBtnMouseLeave() {
  if (hoverLoginTimer !== null) clearTimeout(hoverLoginTimer)
  hoverLoginTimer = null
  isHoveringLogin.value = false
}

// ===== 滑动验证码 =====
const needCaptcha = ref(false)
const captchaId = ref('')
const captchaTargetX = ref(50)
const sliderX = ref(0)
const isDragging = ref(false)
const sliderVerified = ref(false)
const sliderTrackRef = ref<HTMLElement | null>(null)

async function checkNeedCaptcha() {
  if (!form.username) return
  try {
    const res = await checkCaptcha(form.username)
    if (res.needCaptcha) {
      needCaptcha.value = true
      captchaId.value = res.captchaId || ''
      captchaTargetX.value = res.targetX || 50
      resetSlider()
    } else {
      needCaptcha.value = false
    }
  } catch {
    // ignore
  }
}

let captchaCheckTimer: ReturnType<typeof setTimeout> | null = null
watch(() => form.username, (val) => {
  if (captchaCheckTimer) clearTimeout(captchaCheckTimer)
  if (val) {
    captchaCheckTimer = setTimeout(checkNeedCaptcha, 500)
  } else {
    needCaptcha.value = false
  }
})

async function refreshCaptcha() {
  if (!form.username) return
  try {
    const res = await checkCaptcha(form.username)
    if (res.needCaptcha) {
      needCaptcha.value = true
      captchaId.value = res.captchaId || ''
      captchaTargetX.value = res.targetX || 50
      resetSlider()
    }
  } catch { /* ignore */ }
}

function resetSlider() {
  sliderX.value = 0
  sliderVerified.value = false
  form.captchaAnswer = undefined
  form.captchaId = undefined
}

function onSliderMouseDown(e: MouseEvent) {
  if (sliderVerified.value) return
  isDragging.value = true
  const startX = e.clientX
  const trackEl = sliderTrackRef.value
  if (!trackEl) return
  const trackWidth = trackEl.clientWidth

  const onMove = (ev: MouseEvent) => {
    const dx = ev.clientX - startX
    const percent = Math.max(0, Math.min(100, (dx / trackWidth) * 100))
    sliderX.value = percent
  }

  const onUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    // 检查是否在目标位置附近
    if (Math.abs(sliderX.value - captchaTargetX.value) <= 5) {
      sliderVerified.value = true
      form.captchaId = captchaId.value
      form.captchaAnswer = String(Math.round(sliderX.value))
    } else {
      // 验证失败，弹回
      setTimeout(() => {
        sliderX.value = 0
      }, 300)
    }
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

// 触摸支持
function onSliderTouchStart(e: TouchEvent) {
  if (sliderVerified.value) return
  isDragging.value = true
  const startX = e.touches[0].clientX
  const trackEl = sliderTrackRef.value
  if (!trackEl) return
  const trackWidth = trackEl.clientWidth

  const onMove = (ev: TouchEvent) => {
    const dx = ev.touches[0].clientX - startX
    const percent = Math.max(0, Math.min(100, (dx / trackWidth) * 100))
    sliderX.value = percent
  }

  const onUp = () => {
    isDragging.value = false
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
    if (Math.abs(sliderX.value - captchaTargetX.value) <= 5) {
      sliderVerified.value = true
      form.captchaId = captchaId.value
      form.captchaAnswer = String(Math.round(sliderX.value))
    } else {
      setTimeout(() => { sliderX.value = 0 }, 300)
    }
  }

  document.addEventListener('touchmove', onMove)
  document.addEventListener('touchend', onUp)
}

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (needCaptcha.value && !sliderVerified.value) {
    ElMessage.warning('请完成滑动验证')
    return
  }
  loading.value = true
  try {
    await userStore.login(form)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
    ElMessage.success('登录成功')
  } catch {
    // 登录失败后重新检查是否需要验证码
    await checkNeedCaptcha()
    resetSlider()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex bg-white dark:bg-zinc-950">
    <!-- Left: Brand + Fun Characters -->
    <div class="hidden lg:flex w-[42%] flex-col justify-between bg-black relative overflow-hidden p-10">
      <!-- Decorative blurs -->
      <div class="absolute inset-0 opacity-5 pointer-events-none">
        <div class="absolute top-20 left-20 w-80 h-80 bg-white rounded-full blur-3xl" />
        <div class="absolute bottom-20 right-20 w-96 h-96 bg-white rounded-full blur-3xl" />
      </div>

      <!-- Top: Logo + Site Name + Slogan -->
      <div class="relative z-10">
        <div class="flex items-center gap-3 text-white">
          <img :src="configStore.siteLogo" alt="Logo" class="w-14 h-14 rounded-xl object-cover" />
          <span class="text-2xl font-semibold tracking-tight">{{ configStore.siteName }}</span>
        </div>
        <p class="mt-2 text-base text-white/80 ml-[4.5rem]">知识管理 · 高效科研</p>
      </div>

      <!-- Middle: Cartoon Characters -->
      <div class="relative z-10 flex items-end justify-center flex-1">
        <LoginCharacters
          :is-typing="isInputFocused"
          :has-password="!!form.password"
          :show-password="showPassword"
          :is-dark-mode="appStore.config.darkMode"
          :is-excited="isHoveringLogin"
        />
      </div>
    </div>

    <!-- Right: Login Form -->
    <div class="flex-1 flex items-center justify-center p-8">
      <div
        class="w-full max-w-sm animate-slide-up"
      >
        <div class="lg:hidden mb-8 text-center">
          <img :src="configStore.siteLogo" alt="Logo" class="w-14 h-14 mx-auto mb-3 rounded-xl object-cover" />
          <h1 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">{{ configStore.siteName }}</h1>
        </div>

        <h2 class="text-2xl font-bold text-slate-800 dark:text-zinc-100 mb-1">欢迎回来</h2>
        <p class="text-sm text-slate-500 dark:text-zinc-400 mb-8">登录您的账号以继续</p>

        <el-form @submit.prevent="handleLogin" class="space-y-4">
          <el-form-item>
            <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" clearable @focus="isInputFocused = true" @blur="isInputFocused = false" />
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.password" :type="showPassword ? 'text' : 'password'" placeholder="密码" size="large" :prefix-icon="Lock" @keyup.enter="handleLogin" @focus="isInputFocused = true" @blur="isInputFocused = false">
              <template #suffix>
                <el-icon class="cursor-pointer" @click="showPassword = !showPassword">
                  <View v-if="!showPassword" />
                  <Hide v-else />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <!-- 滑动验证码（失败3次后显示） -->
          <el-form-item v-if="needCaptcha">
            <div class="w-full">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-xs text-slate-500">请拖动滑块到指定位置完成验证</span>
                <el-button size="small" text :icon="RefreshRight" @click="refreshCaptcha" class="!p-0 !text-xs">刷新</el-button>
              </div>
              <div
                ref="sliderTrackRef"
                class="relative h-10 rounded-lg select-none overflow-hidden"
                :style="{ background: sliderVerified ? '#dcfce7' : '#f1f5f9' }"
              >
                <!-- 目标位置指示 -->
                <div
                  v-if="!sliderVerified"
                  class="absolute top-0 bottom-0 w-1 rounded"
                  :style="{ left: captchaTargetX + '%', background: '#3b82f6', opacity: 0.6 }"
                />
                <!-- 已滑动区域 -->
                <div
                  class="absolute top-0 bottom-0 left-0 rounded-lg transition-colors"
                  :style="{
                    width: sliderX + '%',
                    background: sliderVerified ? '#22c55e' : '#93c5fd'
                  }"
                />
                <!-- 滑块 -->
                <div
                  class="absolute top-0 bottom-0 w-10 flex items-center justify-center cursor-grab rounded-lg shadow-md transition-colors"
                  :class="isDragging ? 'cursor-grabbing' : '', sliderVerified ? 'bg-green-500 text-white' : 'bg-white text-slate-400 hover:text-primary-500'"
                  :style="{ left: `calc(${sliderX}% - ${sliderX > 0 ? 40 * sliderX / 100 : 0}px)` }"
                  @mousedown="onSliderMouseDown"
                  @touchstart="onSliderTouchStart"
                >
                  <svg v-if="!sliderVerified" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                  <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path d="M5 13l4 4L19 7"/></svg>
                </div>
                <!-- 提示文字 -->
                <div
                  v-if="!sliderVerified && sliderX === 0"
                  class="absolute inset-0 flex items-center justify-center text-xs text-slate-400 pointer-events-none"
                >向右拖动滑块</div>
                <div
                  v-if="sliderVerified"
                  class="absolute inset-0 flex items-center justify-center text-xs text-green-700 font-medium pointer-events-none"
                >验证通过</div>
              </div>
            </div>
          </el-form-item>

          <div class="flex items-center justify-between">
            <el-checkbox v-model="rememberMe" size="small">记住登录</el-checkbox>
            <router-link to="/forgot-password" class="text-xs text-primary-500 hover:text-primary-600 transition-colors">忘记密码？</router-link>
          </div>

          <el-button
            type="primary"
            size="large"
            class="w-full"
            :loading="loading"
            :disabled="!form.username || !form.password || (needCaptcha && !sliderVerified)"
            @click="handleLogin"
            @mouseenter="onLoginBtnMouseEnter"
            @mouseleave="onLoginBtnMouseLeave"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form>

        <div class="mt-6 text-center text-sm text-slate-500 dark:text-zinc-400">
          还没有账号？
          <router-link to="/register" class="text-primary-500 hover:text-primary-600 font-medium transition-colors">立即注册</router-link>
        </div>

        <div class="mt-8 flex justify-center gap-4 text-slate-400">
          <button @click="appStore.toggleDarkMode()" class="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors">
            <el-icon :size="18"><Moon v-if="!appStore.config.darkMode" /><Sunny v-else /></el-icon>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
